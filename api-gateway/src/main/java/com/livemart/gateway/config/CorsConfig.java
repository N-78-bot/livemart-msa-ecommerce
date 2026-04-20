package com.livemart.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 명시적 CorsWebFilter — globalcors YAML 설정 외 추가 보장
 * Spring Cloud Gateway WebFlux CORS 처리 순서 이슈 대응
 *
 * 주의: Next.js rewrite 프록시는 Origin 헤더를 그대로 전달하므로
 * 이 필터가 globalcors YAML보다 먼저 실행되어 Vercel 도메인을 반드시 허용해야 함.
 */
@Configuration
public class CorsConfig {

    @Value("${cors.allowed-origins:}")
    private String corsAllowedOriginsEnv;

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();

        List<String> origins = new ArrayList<>(List.of(
                "http://localhost",
                "http://localhost:3000",
                "http://localhost:3001",
                "http://localhost:3002",
                // Vercel 프로덕션 도메인 (Next.js rewrite가 Origin 헤더를 그대로 전달)
                "https://livemart-parkmin-jes-projects.vercel.app"
        ));

        // 환경변수 CORS_ALLOWED_ORIGINS로 추가 도메인 주입 (콤마 구분)
        if (corsAllowedOriginsEnv != null && !corsAllowedOriginsEnv.isBlank()) {
            Arrays.stream(corsAllowedOriginsEnv.split(","))
                  .map(String::trim)
                  .filter(s -> !s.isEmpty())
                  .forEach(origins::add);
        }

        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of(
                HttpMethod.GET.name(),
                HttpMethod.POST.name(),
                HttpMethod.PUT.name(),
                HttpMethod.DELETE.name(),
                HttpMethod.PATCH.name(),
                HttpMethod.OPTIONS.name()
        ));
        config.setAllowedHeaders(List.of(
                HttpHeaders.AUTHORIZATION,
                HttpHeaders.CONTENT_TYPE,
                "X-API-Key",
                "X-Requested-With",
                HttpHeaders.COOKIE
        ));
        config.setExposedHeaders(List.of(HttpHeaders.SET_COOKIE));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }
}
