package com.livemart.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

/**
 * API Gateway Rate Limiting 설정 (Redis Token Bucket 알고리즘)
 *
 * 전략:
 * - 인증 사용자: JWT sub(userId)를 키로 사용 → 사용자별 제한
 * - 비인증 요청: IP 주소를 키로 사용
 * - replenishRate: 초당 충전 토큰 수 (정상 처리량)
 * - burstCapacity: 버킷 최대 용량 (순간 최대 처리량)
 *
 * 티어별 Rate Limit:
 * - 일반 API: 100 req/s, burst 200
 * - 주문/결제 API: 20 req/s, burst 40 (오남용 방지)
 */
@Configuration
public class RateLimiterConfig {

    /**
     * 기본 KeyResolver: Authorization 헤더의 JWT subject(userId) 또는 IP 기반
     */
    @Bean
    @Primary
    public KeyResolver userKeyResolver() {
        return exchange -> {
            String auth = exchange.getRequest().getHeaders().getFirst("Authorization");
            if (auth != null && auth.startsWith("Bearer ")) {
                // JWT payload의 sub(userId) 추출 (Base64 디코딩)
                try {
                    String payload = auth.substring(7).split("\\.")[1];
                    String decoded = new String(java.util.Base64.getUrlDecoder().decode(
                            payload + "=".repeat((4 - payload.length() % 4) % 4)
                    ));
                    String userId = decoded.replaceAll(".*\"sub\":\"?(\\w+)\"?.*", "$1");
                    return Mono.just("user:" + userId);
                } catch (Exception ignored) { /* fallback to IP */ }
            }
            // 비인증 요청: IP 기반
            String ip = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
            if (ip == null && exchange.getRequest().getRemoteAddress() != null) {
                ip = exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
            }
            return Mono.just("ip:" + (ip != null ? ip : "unknown"));
        };
    }

    /**
     * 일반 API Rate Limiter: 100 req/s, burst 200
     */
    @Bean
    public RedisRateLimiter defaultRateLimiter() {
        return new RedisRateLimiter(100, 200, 1);
    }

    /**
     * 주문/결제 API Rate Limiter: 20 req/s, burst 40
     * 중복 주문, 결제 남용 방지
     */
    @Bean
    public RedisRateLimiter orderRateLimiter() {
        return new RedisRateLimiter(20, 40, 1);
    }
}
