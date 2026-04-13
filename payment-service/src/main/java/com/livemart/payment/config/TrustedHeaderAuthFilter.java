package com.livemart.payment.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * API Gateway가 JWT를 검증 후 주입한 X-User-Id / X-User-Role 헤더를 읽어
 * Spring Security Authentication 컨텍스트를 설정한다.
 * - 클러스터 내부에서만 접근 가능한 포트에서 실행되므로 헤더 위조 위험 없음
 * - SecurityContext 설정으로 @PreAuthorize 어노테이션 활성화
 */
@Component
public class TrustedHeaderAuthFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String userId = request.getHeader("X-User-Id");
        String role   = request.getHeader("X-User-Role");

        if (StringUtils.hasText(userId) && StringUtils.hasText(role)) {
            List<SimpleGrantedAuthority> authorities = List.of(
                    new SimpleGrantedAuthority("ROLE_" + role)
            );
            try {
                Long id = Long.parseLong(userId);
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(id, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (NumberFormatException ignored) {
            }
        }

        filterChain.doFilter(request, response);
    }
}
