package com.farm.smart.common;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * 限流切面
 * <p>
 * 基于 Redis 滑动窗口 + AOP 实现接口限流，
 * 超过阈值抛出 BusinessException（429 Too Many Requests）。
 *
 * @author Smart Farm Team
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private final StringRedisTemplate redisTemplate;

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint point, RateLimit rateLimit) throws Throwable {
        String key = buildKey(point, rateLimit);
        int period = rateLimit.period();
        int count = rateLimit.count();

        // Redis INCR + EXPIRE 实现固定窗口限流
        Long current = redisTemplate.opsForValue().increment(key);
        if (current != null && current == 1) {
            redisTemplate.expire(key, period, TimeUnit.SECONDS);
        }

        if (current != null && current > count) {
            log.warn("接口限流触发: key={}, current={}, limit={}", key, current, count);
            throw new BusinessException(ResultCode.RATE_LIMITED);
        }

        return point.proceed();
    }

    private String buildKey(ProceedingJoinPoint point, RateLimit rateLimit) {
        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();
        String methodKey = rateLimit.key().isEmpty() ? method.getName() : rateLimit.key();

        StringBuilder sb = new StringBuilder("rate_limit:").append(methodKey);

        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest request = attrs.getRequest();
            switch (rateLimit.limitType()) {
                case IP -> sb.append(":ip:").append(getClientIp(request));
                case USER -> sb.append(":user:").append(request.getAttribute("currentUsername"));
                case GLOBAL -> { /* 不追加维度 */ }
            }
        }

        return sb.toString();
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty()) {
            ip = request.getRemoteAddr();
        }
        return ip != null && ip.contains(",") ? ip.split(",")[0].trim() : ip;
    }
}
