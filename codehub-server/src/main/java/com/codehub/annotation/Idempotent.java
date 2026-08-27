package com.codehub.annotation;

import java.lang.annotation.*;

/**
 * 幂等性注解 — 防止重复提交
 *
 * 原理：Redis SETNX + 过期时间
 * 同一用户在窗口期内重复调用同一接口，直接拒绝
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
