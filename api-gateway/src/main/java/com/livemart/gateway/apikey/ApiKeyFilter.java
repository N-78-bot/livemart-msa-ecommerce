package com.livemart.gateway.apikey;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * API Key 인증 필터
 *
 * 인증 우선순위:
 * 1. 공개 경로 → 인증 없이 통과
 * 2. JWT 쿠키(access_token) 또는 Authorization: Bearer → 다운스트림 서비스에서 검증
 * 3. X-API-Key 헤더 → 이 필터에서 직접 검증 (B2B 클라이언트용)
 * 4. 아무것도 없음 → 401 반환
 */
@Component
@Slf4j
public class ApiKeyFilter implements GlobalFilter, Ordered {

    private final ApiKeyService apiKeyService;
    private final SecretKey jwtSecretKey;

    public ApiKeyFilter(ApiKeyService apiKeyService,
                        @Value("${jwt.secret}") String jwtSecret) {
        this.apiKeyService = apiKeyService;
        this.jwtSecretKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    private static final String API_KEY_HEADER = "X-API-Key";

    /** 인증 없이 접근 가능한 공개 경로 prefix 목록 */
    private static final List<String> PUBLIC_PATHS = List.of(
        // 사용자 인증
        "/api/users/login",
        "/api/users/signup",
        "/api/users/refresh",
        "/api/users/email/send",
        "/api/users/email/verify",
        "/api/users/health",
        // OAuth2
        "/oauth2/",
        "/login/oauth2/",
        // 상품 조회 (공개)
        "/api/products",
        "/api/categories",
        // 쿠폰 조회 (공개)
        "/api/coupons",
        // AI 서비스 (챗봇, 추천, 설명생성 — 비로그인 사용자도 사용 가능)
        "/api/ai",
        // 결제 웹훅 (외부 PG사 콜백)
        "/api/v1/payments/webhook",
        // 헬스체크 & 모니터링
        "/actuator",
        "/api/v1/health"
        // 주의: /api/v1/api-keys는 공개 경로에서 제거됨 — JWT 인증 필요
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        // 1. 공개 경로 → X-User-* 헤더 제거 후 통과 (헤더 위조 방지)
        if (isPublicPath(path)) {
            ServerHttpRequest stripped = request.mutate()
                    .headers(h -> {
                        h.remove("X-User-Id");
                        h.remove("X-User-Role");
                        h.remove("X-User-Email");
                    })
                    .build();
            return chain.filter(exchange.mutate().request(stripped).build());
        }

        // 2. JWT 쿠키 또는 Authorization Bearer → 서명 검증 후 사용자 컨텍스트 헤더 주입
        if (hasJwtAuth(request)) {
            log.debug("JWT auth detected: path={}", path);
            ServerHttpRequest mutated = injectUserContextHeaders(request);
            if (mutated == null) {
                // JWT 서명 검증 실패
                return unauthorized(exchange, "Invalid or expired JWT token");
            }
            return chain.filter(exchange.mutate().request(mutated).build());
        }

        // 3. API Key 검증 (B2B 클라이언트)
        String apiKey = request.getHeaders().getFirst(API_KEY_HEADER);

        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("No auth provided: path={}", path);
            return unauthorized(exchange, "Authentication required: provide X-API-Key header or valid JWT cookie");
        }

        String ipAddress = getClientIpAddress(request);
        ApiKeyService.ValidationResult result = apiKeyService.validateApiKey(apiKey, ipAddress);

        if (!result.valid()) {
            log.warn("Invalid API Key: apiKey={}, reason={}, ip={}",
                     maskApiKey(apiKey), result.message(), ipAddress);
            return unauthorized(exchange, result.message());
        }

        // API Key 검증 성공 → 위조 헤더 제거 후 검증된 사용자 정보 주입
        ServerHttpRequest mutatedRequest = request.mutate()
            .headers(h -> {
                h.remove("X-User-Id");
                h.remove("X-User-Role");
                h.remove("X-User-Email");
            })
            .header("X-User-Id", String.valueOf(result.keyInfo().userId()))
            .header("X-API-Key-Name", result.keyInfo().name())
            .build();

        log.debug("API Key validated: apiKey={}, userId={}, path={}",
                  maskApiKey(apiKey), result.keyInfo().userId(), path);

        return chain.filter(exchange.mutate().request(mutatedRequest).build())
            .onErrorResume(throwable -> {
                apiKeyService.incrementErrors(apiKey);
                return Mono.error(throwable);
            });
    }

    /**
     * JWT 서명 검증 후 사용자 컨텍스트 헤더(X-User-Id, X-User-Role, X-User-Email) 주입.
     * 클라이언트가 전송한 X-User-* 헤더를 먼저 제거해 헤더 위조(Spoofing) 방지.
     * 서명 검증 실패 시 요청 거부 (401).
     */
    private ServerHttpRequest injectUserContextHeaders(ServerHttpRequest request) {
        String token = extractJwtToken(request);
        if (token == null) return request;
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(jwtSecretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            // 클라이언트 위조 헤더 제거 후 검증된 값으로 교체
            ServerHttpRequest.Builder builder = request.mutate()
                    .headers(h -> {
                        h.remove("X-User-Id");
                        h.remove("X-User-Role");
                        h.remove("X-User-Email");
                    });
            if (claims.getSubject() != null) builder.header("X-User-Id", claims.getSubject());
            String role = claims.get("role", String.class);
            if (role != null) builder.header("X-User-Role", role);
            String email = claims.get("email", String.class);
            if (email != null) builder.header("X-User-Email", email);
            return builder.build();
        } catch (JwtException e) {
            log.warn("JWT 서명 검증 실패: {}", e.getMessage());
            return null; // 검증 실패 시 null 반환 → 호출자가 401 처리
        } catch (Exception e) {
            log.debug("JWT 파싱 실패: {}", e.getMessage());
            return null;
        }
    }

    private String extractJwtToken(ServerHttpRequest request) {
        List<String> cookies = request.getHeaders().get("Cookie");
        if (cookies != null) {
            for (String cookieHeader : cookies) {
                for (String part : cookieHeader.split(";")) {
                    String trimmed = part.trim();
                    if (trimmed.startsWith("access_token=")) {
                        return trimmed.substring("access_token=".length());
                    }
                }
            }
        }
        String auth = request.getHeaders().getFirst("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7);
        }
        return null;
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }

    /**
     * JWT 인증 존재 여부 확인
     * - access_token 쿠키 (브라우저/httpOnly)
     * - Authorization: Bearer 헤더 (Swagger, 모바일 클라이언트)
     */
    private boolean hasJwtAuth(ServerHttpRequest request) {
        // httpOnly 쿠키 확인
        List<String> cookies = request.getHeaders().get("Cookie");
        if (cookies != null) {
            boolean hasCookie = cookies.stream()
                .anyMatch(c -> c.contains("access_token="));
            if (hasCookie) return true;
        }

        // Authorization: Bearer 헤더 확인
        String auth = request.getHeaders().getFirst("Authorization");
        return auth != null && auth.startsWith("Bearer ");
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add("Content-Type", "application/json");

        String body = String.format("{\"error\":\"Unauthorized\",\"message\":\"%s\"}", message);

        return response.writeWith(
            Mono.just(response.bufferFactory().wrap(body.getBytes()))
        );
    }

    private String getClientIpAddress(ServerHttpRequest request) {
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeaders().getFirst("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        return request.getRemoteAddress() != null
            ? request.getRemoteAddress().getAddress().getHostAddress()
            : "unknown";
    }

    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() < 10) return "***";
        return apiKey.substring(0, 7) + "..." + apiKey.substring(apiKey.length() - 4);
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
