package com.codehub.aspect;

import com.codehub.annotation.Idempotent;
import com.codehub.common.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

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

    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        HttpServletRequest request = ((ServletRequestAttributes)
                RequestContextHolder.currentRequestAttributes()).getRequest();

        // 未登录的请求不做幂等控制（由鉴权层负责）
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return joinPoint.proceed();
        }

        String key = buildKey(userId, joinPoint);
        Boolean firstTime = redisTemplate.opsForValue()
                .setIfAbsent(key, "1", idempotent.windowSeconds(), TimeUnit.SECONDS);

        if (firstTime == null || !firstTime) {
            log.warn("重复提交被拦截: user={}, key={}", userId, key);
            throw new BusinessException(429, idempotent.message());
        }

        try {
            return joinPoint.proceed();
        } catch (Throwable e) {
            // 业务失败时释放幂等标记，允许用户立即重试
            redisTemplate.delete(key);
            throw e;
        }
    }

    /**
     * Key = 注解所在类.方法名 + 参数内容摘要
     * 参数相同才算重复（改名后重新提交是合法操作）
     */
    private String buildKey(Long userId, ProceedingJoinPoint joinPoint) {
        String method = joinPoint.getSignature().toShortString();
        StringBuilder args = new StringBuilder();
        for (Object arg : joinPoint.getArgs()) {
            // 跳过HttpServletRequest等非业务参数
            if (arg != null && !(arg instanceof HttpServletRequest)) {
                args.append(arg.toString()).append("|");
            }
        }
        return "idempotent:" + userId + ":" + method + ":" + args.hashCode();
    }
}
