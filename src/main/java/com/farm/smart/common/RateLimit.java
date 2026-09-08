package com.farm.smart.common;

import java.lang.annotation.*;

/**
 * 接口限流注解
 * <p>
 * 基于 Redis 滑动窗口实现，防止恶意请求和突发流量。
 * 可标注在 Controller 方法上，支持按 IP + URI 维度限流。
 *
 * @author Smart Farm Team
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    /**
     * 限流 key（默认使用方法名）
     */
    String key() default "";

    /**
     * 时间窗口（秒），默认 60 秒
     */
    int period() default 60;

    /**
     * 窗口内最大请求数，默认 100
     */
    int count() default 100;

    /**
     * 限流维度：IP-按IP限流 / USER-按用户限流 / GLOBAL-全局限流
     */
    LimitType limitType() default LimitType.IP;

    enum LimitType {
        IP,
        USER,
        GLOBAL
    }
}
