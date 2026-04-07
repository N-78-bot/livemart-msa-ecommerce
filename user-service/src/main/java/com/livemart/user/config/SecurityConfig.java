package com.livemart.user.config;

import com.livemart.user.oauth.CustomOAuth2UserService;
import com.livemart.user.oauth.HttpCookieOAuth2AuthorizationRequestRepository;
import com.livemart.user.oauth.OAuth2SuccessHandler;
import com.livemart.user.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;
    private final HttpCookieOAuth2AuthorizationRequestRepository cookieAuthorizationRequestRepository;

    @Autowired
    private ClientRegistrationRepository clientRegistrationRepository;

    @Value("${oauth2.redirect-uri:http://localhost:3002/auth/callback}")
    private String oauth2RedirectUri;

    @Value("${cors.allowed-origins:http://localhost:3000,http://localhost:3001,http://localhost:3002}")
    private String corsAllowedOrigins;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        List<String> origins = new java.util.ArrayList<>(List.of(
            "http://localhost", "http://localhost:3000", "http://localhost:3001", "http://localhost:3002"
        ));
        if (corsAllowedOrigins != null && !corsAllowedOrigins.isBlank()) {
            for (String origin : corsAllowedOrigins.split(",")) {
                String trimmed = origin.trim();
                if (!trimmed.isEmpty() && !origins.contains(trimmed)) {
                    origins.add(trimmed);
                }
            }
        }
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            // Form 로그인 비활성화 (JWT + OAuth2만 사용)
            .formLogin(AbstractHttpConfigurer::disable)
            // OAuth2는 세션 필요, IF_REQUIRED 사용
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/api/users/signup",
                    "/api/users/login",
                    "/api/users/refresh",
                    "/api/users/health",
                    "/api/users/email/**",   // 이메일 인증
                    "/oauth2/**",            // OAuth2 진입점
                    "/login/oauth2/**",      // OAuth2 콜백
                    "/api/users/session-logout", // 세션 로그아웃 (비인증 허용)
                    "/actuator/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/api-docs/**",
                    "/v3/api-docs/**"
                ).permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .logout(logout -> logout
                .logoutUrl("/api/users/session-logout")
                .invalidateHttpSession(true)          // 서버 세션 무효화
                .clearAuthentication(true)            // SecurityContext 초기화
                .deleteCookies("JSESSIONID")          // 세션 쿠키 삭제
                .logoutSuccessHandler((request, response, authentication) -> {
                    response.setStatus(HttpServletResponse.SC_OK);
                })
            )
            .oauth2Login(oauth2 -> oauth2
                .authorizationEndpoint(endpoint -> endpoint
                    .authorizationRequestRepository(cookieAuthorizationRequestRepository)
                    .authorizationRequestResolver(oAuth2AuthorizationRequestResolver()))
                .userInfoEndpoint(userInfo -> userInfo
                    .userService(customOAuth2UserService))
                .successHandler(oAuth2SuccessHandler)
                .failureHandler((request, response, exception) -> {
                    org.slf4j.LoggerFactory.getLogger(SecurityConfig.class)
                        .error("OAuth2 login FAILED — type={}, msg={}",
                            exception.getClass().getSimpleName(), exception.getMessage(), exception);
                    String baseUrl = oauth2RedirectUri.replaceAll("/auth/callback.*", "");
                    response.sendRedirect(baseUrl + "/auth?error=oauth2_failed");
                })
            )
            // API 요청은 JSON 401/403 반환 (브라우저 리다이렉트 방지)
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"로그인이 필요합니다\"}");
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"error\":\"Forbidden\",\"message\":\"접근 권한이 없습니다\"}");
                })
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * OAuth2 인증 요청 커스터마이저
     * - Google: prompt=select_account → 항상 계정 선택 화면 표시
     * - Kakao:  prompt=login         → 항상 재로그인 화면 표시
     * - Naver:  auth_type=reauthenticate (선택적)
     */
    private OAuth2AuthorizationRequestResolver oAuth2AuthorizationRequestResolver() {
        DefaultOAuth2AuthorizationRequestResolver resolver =
            new DefaultOAuth2AuthorizationRequestResolver(
                clientRegistrationRepository, "/oauth2/authorization");

        resolver.setAuthorizationRequestCustomizer(customizer -> {
            String registrationId = (String) customizer.build()
                .getAttributes().get(OAuth2ParameterNames.REGISTRATION_ID);

            if ("google".equals(registrationId)) {
                // 항상 계정 선택 화면 (로그아웃 후 다른 계정으로 로그인 가능)
                customizer.additionalParameters(p -> p.put("prompt", "select_account"));
            } else if ("kakao".equals(registrationId)) {
                // 항상 카카오 재로그인 화면 표시
                customizer.additionalParameters(p -> p.put("prompt", "login"));
            }
        });

        return resolver;
    }
}
