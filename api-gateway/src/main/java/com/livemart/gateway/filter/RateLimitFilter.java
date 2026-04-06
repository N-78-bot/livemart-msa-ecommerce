package com.livemart.gateway.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Redis Sliding Window Rate Limiting 글로벌 필터
 *
 * 전략:
 * - 인증 사용자: JWT sub(userId) 기반 개인화 Rate Limit
 * - 비인증 요청: IP 기반 Rate Limit
 *
 * 엔드포인트별 차등 제한:
 *   GET  /api/products/**        : 200 req/min/user (캐시로 커버)
 *   POST /api/orders             : 10  req/min/user  (오남용 방지)
 *   POST /api/payments/**        : 5   req/min/user  (결제 오남용 방지)
 *   POST /api/auth/login         : 10  req/min/IP    (Brute Force 방지)
 *   GET  /api/products/search/** : 100 req/min/user  (검색 엔진 보호)
 *   기타                         : 60  req/min/user
 *
 * Rate Limit 초과 응답:
 *   HTTP 429 Too Many Requests
 *   Retry-After: N (다음 요청 가능까지 초)
 *   X-RateLimit-Limit: 최대 요청 수
 *   X-RateLimit-Remaining: 남은 요청 수
 *   X-RateLimit-Reset: 윈도우 리셋 시각 (Unix timestamp)
 *
 * 메트릭:
 *   gateway.rate_limit.allowed (counter, tags: endpoint, user_type)
 *   gateway.rate_limit.rejected (counter, tags: endpoint, user_type)
 */
@Slf4j
@Component
public class RateLimitFilter implements GlobalFilter, Ordered {

    private static final int FILTER_ORDER = -5; // AuthFilter 다음, 라우팅 전

    // Lua 스크립트 (애플리케이션 시작 시 1회 로딩 후 캐싱)
    private final RedisScript<List<Long>> rateLimitScript;
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final MeterRegistry meterRegistry;

    // 엔드포인트별 Rate Limit 설정 (window_ms, max_requests)
    private static final Map<String, long[]> ENDPOINT_LIMITS = Map.of(
        "POST:/api/orders",          new long[]{60_000L, 10L},   // 10 req/min
        "POST:/api/payments",        new long[]{60_000L, 5L},    // 5 req/min
        "POST:/api/auth/login",      new long[]{60_000L, 10L},   // 10 req/min (IP)
        "GET:/api/products/search",  new long[]{60_000L, 100L},  // 100 req/min
        "GET:/api/products",         new long[]{60_000L, 200L},  // 200 req/min
        "POST:/api/ai",              new long[]{60_000L, 20L}    // 20 req/min (AI 비용)
    );
    private static final long[] DEFAULT_LIMIT = new long[]{60_000L, 60L}; // 60 req/min

    private Counter allowedCounter;
    private Counter rejectedCounter;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public RateLimitFilter(ReactiveRedisTemplate<String, String> redisTemplate,
                           MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.meterRegistry = meterRegistry;
        this.rateLimitScript = loadLuaScript();

        // Prometheus 메트릭 초기화
        this.allowedCounter = Counter.builder("gateway.rate_limit.allowed")
            .description("Rate limit 통과 요청 수")
            .register(meterRegistry);
        this.rejectedCounter = Counter.builder("gateway.rate_limit.rejected")
            .description("Rate limit 거부 요청 수")
            .register(meterRegistry);
    }

    @Override
    public int getOrder() {
        return FILTER_ORDER;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        String method = exchange.getRequest().getMethod().name();

        // Rate Limit 제외 경로
        if (shouldSkip(path)) {
            return chain.filter(exchange);
        }

        // Rate Limit 키 결정
        String rateLimitKey = buildRateLimitKey(exchange, method, path);
        boolean isIpBased = rateLimitKey.startsWith("rl:ip:");
        String userType = isIpBased ? "anonymous" : "authenticated";

        // 엔드포인트별 제한 설정 조회
        long[] limits = getEndpointLimits(method, path);
        long windowMs = limits[0];
        long maxRequests = limits[1];
        long ttlSeconds = (windowMs / 1000) + 10;

        long now = System.currentTimeMillis();

        // Redis Lua 스크립트 실행 (원자적 처리)
        return redisTemplate.execute(
                rateLimitScript,
                List.of(rateLimitKey),
                List.of(
                    String.valueOf(now),
                    String.valueOf(windowMs),
                    String.valueOf(maxRequests),
                    String.valueOf(ttlSeconds)
                )
            )
            .collectList()
            .flatMap(results -> {
                if (results.isEmpty() || results.get(0) == null) {
                    // Redis 오류 시 허용 (Fail Open 정책)
                    log.warn("Rate Limit Redis 실행 실패, 요청 허용 (Fail Open): key={}", rateLimitKey);
                    return chain.filter(exchange);
                }

                List<Long> result = (List<Long>) results.get(0);
                long allowed = result.get(0);
                long currentCount = result.get(1);
                long remaining = result.get(2);

                if (allowed == 1) {
                    allowedCounter.increment();
                    // 응답 헤더에 Rate Limit 정보 추가
                    exchange.getResponse().getHeaders().add("X-RateLimit-Limit", String.valueOf(maxRequests));
                    exchange.getResponse().getHeaders().add("X-RateLimit-Remaining", String.valueOf(remaining));
                    exchange.getResponse().getHeaders().add("X-RateLimit-Reset",
                        String.valueOf((now + windowMs) / 1000));
                    return chain.filter(exchange);
                } else {
                    rejectedCounter.increment();
                    log.warn("Rate Limit 초과: key={}, count={}/{}, endpoint={}:{}",
                        rateLimitKey, currentCount, maxRequests, method, path);

                    return handleRateLimitExceeded(exchange, maxRequests, windowMs);
                }
            })
            .onErrorResume(ex -> {
                log.error("Rate Limit 필터 오류 (Fail Closed): {}", ex.getMessage());
                // 결제/주문 엔드포인트는 Fail-Closed (Redis 없으면 차단)
                String errorPath = exchange.getRequest().getPath().value();
                if (errorPath.startsWith("/api/payments") || errorPath.startsWith("/api/orders")) {
                    exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
                    return exchange.getResponse().setComplete();
                }
                // 일반 API는 Fail-Open 유지 (가용성)
                return chain.filter(exchange);
            });
    }

    private Mono<Void> handleRateLimitExceeded(ServerWebExchange exchange,
                                                long maxRequests, long windowMs) {
        long retryAfterSeconds = windowMs / 1000;

        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().add("Retry-After", String.valueOf(retryAfterSeconds));
        exchange.getResponse().getHeaders().add("X-RateLimit-Limit", String.valueOf(maxRequests));
        exchange.getResponse().getHeaders().add("X-RateLimit-Remaining", "0");
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = String.format(
            "{\"error\":\"Too Many Requests\",\"message\":\"요청 한도를 초과했습니다. %d초 후 다시 시도하세요.\",\"retryAfter\":%d}",
            retryAfterSeconds, retryAfterSeconds
        );
        org.springframework.core.io.buffer.DataBuffer buffer =
            exchange.getResponse().bufferFactory().wrap(body.getBytes());
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    /**
     * Rate Limit 키 생성
     * - 인증 사용자: rl:user:{userId}:{method}:{path_prefix}
     * - 비인증 사용자: rl:ip:{ip}:{method}:{path_prefix}
     */
    private String buildRateLimitKey(ServerWebExchange exchange, String method, String path) {
        // JWT에서 userId 추출 시도 (Jackson 사용 — 정규식 취약점 제거)
        String auth = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            String userId = extractUserIdFromToken(auth.substring(7));
            if (userId != null) {
                String pathPrefix = getPathPrefix(path);
                return String.format("rl:user:%s:%s:%s", userId, method, pathPrefix);
            }
        }

        // IP 기반 (비인증)
        String ip = getClientIp(exchange);
        String pathPrefix = getPathPrefix(path);
        return String.format("rl:ip:%s:%s:%s", ip, method, pathPrefix);
    }

    /**
     * JWT payload에서 sub 클레임 추출 — Jackson 파싱으로 정규식 취약점 제거
     */
    private String extractUserIdFromToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) return null;
            String paddedPayload = parts[1] + "=".repeat((4 - parts[1].length() % 4) % 4);
            String payload = new String(Base64.getUrlDecoder().decode(paddedPayload));
            JsonNode node = objectMapper.readTree(payload);
            JsonNode sub = node.get("sub");
            return sub != null ? sub.asText() : null;
        } catch (Exception e) {
            log.warn("JWT 페이로드 파싱 실패: {}", e.getMessage());
            return null;
        }
    }

    private String getClientIp(ServerWebExchange exchange) {
        String xForwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        if (exchange.getRequest().getRemoteAddress() != null) {
            return exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        }
        return "unknown";
    }

    private String getPathPrefix(String path) {
        // /api/products/123 → /api/products
        // /api/orders/456/cancel → /api/orders
        String[] parts = path.split("/");
        if (parts.length >= 3) {
            return "/" + parts[1] + "/" + parts[2];
        }
        return path;
    }

    private long[] getEndpointLimits(String method, String path) {
        String pathPrefix = getPathPrefix(path);
        String key = method + ":" + pathPrefix;

        return ENDPOINT_LIMITS.entrySet().stream()
            .filter(e -> key.startsWith(e.getKey()))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse(DEFAULT_LIMIT);
    }

    private boolean shouldSkip(String path) {
        return path.startsWith("/actuator")
            || path.startsWith("/health")
            || path.startsWith("/swagger")
            || path.startsWith("/v3/api-docs");
    }

    @SuppressWarnings("unchecked")
    private RedisScript<List<Long>> loadLuaScript() {
        try {
            return RedisScript.of(
                new ClassPathResource("lua/sliding-window-rate-limit.lua"),
                (Class<List<Long>>) (Class<?>) List.class
            );
        } catch (Exception e) {
            log.error("Lua 스크립트 로딩 실패: {}", e.getMessage());
            throw new RuntimeException("Rate Limit Lua 스크립트 초기화 실패", e);
        }
    }
}
