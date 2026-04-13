package com.livemart.payment.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 마이크로서비스 보안 설정
 * - 실제 인증/인가는 API Gateway에서 처리 (JWT 검증)
 * - TrustedHeaderAuthFilter: X-User-Id / X-User-Role 헤더로 SecurityContext 설정
 * - @PreAuthorize 어노테이션 기반 메서드 레벨 접근 제어 활성화
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final TrustedHeaderAuthFilter trustedHeaderAuthFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .addFilterBefore(trustedHeaderAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
