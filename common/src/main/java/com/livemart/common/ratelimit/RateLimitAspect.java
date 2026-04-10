package com.livemart.common.ratelimit;

import com.livemart.common.exception.BusinessException;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate Limiting AOP
 * API 요청 제한을 위한 Aspect (Resilience4j 기반)
 */
@Aspect
@Component
@Slf4j
public class RateLimitAspect {

    private final RateLimiterRegistry rateLimiterRegistry;
    private final ConcurrentHashMap<String, RateLimiter> rateLimiters = new ConcurrentHashMap<>();

    public RateLimitAspect() {
        this.rateLimiterRegistry = RateLimiterRegistry.of(RateLimiterConfig.custom()
                .limitForPeriod(100)
                .limitRefreshPeriod(Duration.ofMinutes(1))
                .timeoutDuration(Duration.ofSeconds(5))
                .build());
    }

    @Around("@annotation(rateLimit)")
    public Object applyRateLimit(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        String key = getRateLimitKey(joinPoint, rateLimit);
        RateLimiter rateLimiter = getRateLimiter(key, rateLimit);

        // Resilience4j 2.x 방식: acquirePermission() 사용
        boolean permission = rateLimiter.acquirePermission();
        if (!permission) {
            log.warn("Rate limit exceeded for key: {}", key);
            throw new BusinessException("RATE_LIMIT_EXCEEDED", "요청 한도를 초과했습니다. 잠시 후 다시 시도해주세요.", 429);
        }

        try {
            return joinPoint.proceed();
        } catch (Throwable throwable) {
            throw throwable;
        }
    }

    private String getRateLimitKey(ProceedingJoinPoint joinPoint, RateLimit rateLimit) {
        if (rateLimit.keyType() == RateLimit.KeyType.IP) {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                String clientIp = attributes.getRequest().getRemoteAddr();
                return rateLimit.name() + ":" + clientIp;
            }
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        return rateLimit.name() + ":" + signature.getMethod().getName();
    }

    private RateLimiter getRateLimiter(String key, RateLimit rateLimit) {
        return rateLimiters.computeIfAbsent(key, k -> {
            RateLimiterConfig config = RateLimiterConfig.custom()
                    .limitForPeriod(rateLimit.limitForPeriod())
                    .limitRefreshPeriod(Duration.ofSeconds(rateLimit.refreshPeriodSeconds()))
                    .timeoutDuration(Duration.ofSeconds(rateLimit.timeoutSeconds()))
                    .build();

            return rateLimiterRegistry.rateLimiter(key, config);
        });
    }
}
