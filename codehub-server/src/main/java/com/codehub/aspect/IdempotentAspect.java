package com.codehub.aspect;

import com.codehub.annotation.Idempotent;
import com.codehub.common.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 幂等性切面
 *
 * Key设计：idempotent:{userId}:{方法签名}:{参数摘要}
 * 同一用户 + 同一接口 + 相同参数，窗口期内只放行一次
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class IdempotentAspect {

    private final StringRedisTemplate redisTemplate;
    private final IdempotencyKeyGenerator keyGenerator;

    // 原子地核对归属并删除，避免过期请求删掉后续请求的标记。
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('del', KEYS[1]) else return 0 end", Long.class);

    @Around("@annotation(com.codehub.annotation.Idempotent)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = AopUtils.getMostSpecificMethod(signature.getMethod(), joinPoint.getTarget().getClass());
        Idempotent idempotent = AnnotationUtils.findAnnotation(method, Idempotent.class);
        if (idempotent == null) {
            return joinPoint.proceed();
        }

        HttpServletRequest request = null;
        for (Object arg : joinPoint.getArgs()) {
            if (arg instanceof HttpServletRequest req) {
                request = req;
                break;
            }
        }
        if (request == null) {
            var requestAttributes = RequestContextHolder.getRequestAttributes();
            if (requestAttributes instanceof ServletRequestAttributes servletAttributes) {
                request = servletAttributes.getRequest();
            }
        }

        if (request == null) {
            log.warn("无法获取HttpServletRequest，跳过幂等控制");
            return joinPoint.proceed();
        }

        // 未登录的请求不做幂等控制（由鉴权层负责）
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            log.info("未获取到userId，跳过幂等检查");
            return joinPoint.proceed();
        }

        if (idempotent.windowSeconds() <= 0) {
            throw new IllegalStateException("防重窗口必须大于0秒");
        }
        String key = keyGenerator.generate(userId, method, joinPoint.getArgs());
        String requestToken = UUID.randomUUID().toString();
        log.info("执行幂等检查: key={}, window={}s", key, idempotent.windowSeconds());

        Boolean firstTime = redisTemplate.opsForValue()
                .setIfAbsent(key, requestToken, idempotent.windowSeconds(), TimeUnit.SECONDS);

        if (firstTime == null) {
            throw new BusinessException(503, "防重服务暂不可用，请稍后重试");
        }
        if (!firstTime) {
            log.warn("重复提交被拦截: user={}, key={}", userId, key);
            throw new BusinessException(429, idempotent.message());
        }

        try {
            return joinPoint.proceed();
        } catch (Throwable e) {
            // 保留原有的异常重试规则，只释放属于本次请求的标记。
            // 此处只做短窗口防重，业务是否回滚由实际事务决定。
            try {
                redisTemplate.execute(RELEASE_SCRIPT, List.of(key), requestToken);
            } catch (Exception releaseFailure) {
                // 保留原始业务异常，清理失败时仍有过期时间兜底。
                log.warn("防重标记释放失败，等待过期: key={}", key, releaseFailure);
                e.addSuppressed(releaseFailure);
            }
            throw e;
        }
    }
}
