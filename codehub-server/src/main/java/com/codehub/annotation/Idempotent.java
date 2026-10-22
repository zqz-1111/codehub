package com.codehub.annotation;

import java.lang.annotation.*;

/**
 * 短时间窗口防重复提交，不替代业务幂等号或数据库唯一约束。
 *
 * 原理：Redis SETNX + 过期时间
 * 同一用户在窗口期内以相同参数重复调用同一接口，直接拒绝。
 * 成功保留标记至过期；抛异常时按请求归属释放标记，允许重试。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {

    /**
     * 幂等窗口（秒），默认5秒内不允许重复提交
     */
    int windowSeconds() default 5;

    /**
     * 提示信息
     */
    String message() default "操作过于频繁，请勿重复提交";
}
