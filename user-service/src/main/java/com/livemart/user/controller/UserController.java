package com.livemart.user.controller;

import com.livemart.user.dto.*;
import com.livemart.user.security.JwtTokenProvider;
import com.livemart.user.service.EmailVerificationService;
import com.livemart.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Arrays;
import java.util.Map;

@Tag(name = "User API", description = "사용자 관리 API")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final EmailVerificationService emailVerificationService;
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${app.cookie.secure:false}")
    private boolean cookieSecure;

    // ─── 인증 ──────────────────────────────────────────────────────

    @Operation(summary = "회원가입")
    @PostMapping("/signup")
    public ResponseEntity<UserResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.signup(request));
    }

    @Operation(summary = "로그인 — 토큰은 httpOnly 쿠키로만 전달")
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response) {

        LoginResult result = userService.login(request);
        setAuthCookies(response, result);

        return ResponseEntity.ok(Map.of(
                "userId", result.getUserId(),
                "email", result.getEmail(),
                "name", result.getName(),
                "role", result.getRole()
        ));
    }

    @Operation(summary = "토큰 갱신 — refresh_token 쿠키 사용")
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refresh(
            HttpServletRequest request,
            HttpServletResponse response) {

        String refreshToken = resolveRefreshToken(request);
        if (refreshToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Refresh token not found"));
        }

        LoginResult result = userService.refresh(refreshToken);
        setAuthCookies(response, result);

        return ResponseEntity.ok(Map.of(
                "userId", result.getUserId(),
                "email", result.getEmail(),
                "name", result.getName(),
                "role", result.getRole()
        ));
    }

    @Operation(summary = "로그아웃 — 쿠키 삭제 + Redis 토큰 제거 + 블랙리스트 등록")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(Authentication authentication,
                                       HttpServletRequest request,
                                       HttpServletResponse response) {
        // access token 블랙리스트 등록 (만료 전 재사용 차단)
        if (request.getCookies() != null) {
            Arrays.stream(request.getCookies())
                    .filter(c -> "access_token".equals(c.getName()))
                    .map(Cookie::getValue)
                    .filter(v -> v != null && !v.isBlank())
                    .findFirst()
                    .ifPresent(jwtTokenProvider::blacklistToken);
        }
        if (authentication != null) {
            Long userId = (Long) authentication.getPrincipal();
            userService.logout(userId);
        }
        // HTTP 세션 무효화 (JSESSIONID 재사용 차단)
        jakarta.servlet.http.HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        clearAuthCookies(response);
        // JSESSIONID 쿠키도 제거
        ResponseCookie sessionCookie = ResponseCookie.from("JSESSIONID", "")
                .httpOnly(true).path("/").maxAge(0).build();
        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie.toString());
        return ResponseEntity.noContent().build();
    }

    // ─── 내 정보 ──────────────────────────────────────────────────

    @Operation(summary = "내 정보 조회")
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getMyProfile(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(userService.getUserById(userId));
    }

    @Operation(summary = "내 프로필 수정 (온보딩·추가정보)")
    @PatchMapping("/me")
    public ResponseEntity<UserResponse> updateMyProfile(
            Authentication authentication,
            @RequestBody UpdateProfileRequest request) {
        Long userId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(userService.updateMyProfile(userId, request));
    }

    @Operation(summary = "사용자 조회")
    @GetMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN') or #userId == authentication.principal")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long userId) {
        return ResponseEntity.ok(userService.getUserById(userId));
    }

    @Operation(summary = "헬스체크")
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("User Service is running");
    }

    @Operation(summary = "비밀번호 변경")
    @PutMapping("/{userId}/password")
    @PreAuthorize("#userId == authentication.principal")
    public ResponseEntity<Void> changePassword(
            @PathVariable Long userId,
            @RequestBody Map<String, String> body) {
        String currentPassword = body.get("currentPassword");
        String newPassword = body.get("newPassword");
        if (currentPassword == null || newPassword == null || newPassword.length() < 8) {
            return ResponseEntity.badRequest().build();
        }
        userService.changePassword(userId, currentPassword, newPassword);
        return ResponseEntity.noContent().build();
    }

    // ─── 관리자 전용 ──────────────────────────────────────────────

    @Operation(summary = "전체 사용자 목록 조회 (관리자)")
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<UserResponse>> getAllUsers(
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(userService.getAllUsers(pageable));
    }

    @Operation(summary = "전체 사용자 수 조회 (관리자)")
    @GetMapping("/count")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Long>> getUserCount() {
        return ResponseEntity.ok(Map.of("count", userService.getUserCount()));
    }

    @Operation(summary = "사용자 역할 변경 (관리자)")
    @PutMapping("/{userId}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> updateUserRole(
            @PathVariable Long userId,
            @RequestBody Map<String, String> body) {
        String role = body.get("role");
        if (role == null || role.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(userService.updateUserRole(userId, role));
    }

    @Operation(summary = "사용자 비활성화 (관리자)")
    @DeleteMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deactivateUser(@PathVariable Long userId) {
        userService.deactivateUser(userId);
        return ResponseEntity.noContent().build();
    }

    // ─── 이메일 인증 ──────────────────────────────────────────────

    @Operation(summary = "이메일 인증 코드 발송")
    @PostMapping("/email/send")
    public ResponseEntity<Map<String, String>> sendVerificationEmail(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "이메일을 입력해주세요"));
        }
        try {
            emailVerificationService.sendVerificationCode(email);
            return ResponseEntity.ok(Map.of("message", "인증 코드가 전송되었습니다"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    @Operation(summary = "이메일 인증 코드 확인")
    @PostMapping("/email/verify")
    public ResponseEntity<Map<String, Object>> verifyEmail(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String code = body.get("code");

        if (email == null || code == null) {
            return ResponseEntity.badRequest().body(Map.of("verified", false, "message", "이메일과 코드를 입력해주세요"));
        }

        boolean verified = emailVerificationService.verifyCode(email, code);
        if (verified) {
            return ResponseEntity.ok(Map.of("verified", true, "message", "인증이 완료되었습니다"));
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("verified", false, "message", "인증 코드가 올바르지 않거나 만료되었습니다"));
        }
    }

    // ─── 쿠키 헬퍼 ───────────────────────────────────────────────

    private void setAuthCookies(HttpServletResponse response, LoginResult result) {
        ResponseCookie accessCookie = ResponseCookie.from("access_token", result.getAccessToken())
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofMillis(result.getAccessTokenExpiry()))
                .build();

        ResponseCookie refreshCookie = ResponseCookie.from("refresh_token", result.getRefreshToken())
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/api/users/refresh")
                .maxAge(Duration.ofMillis(result.getRefreshTokenExpiry()))
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
    }

    private void clearAuthCookies(HttpServletResponse response) {
        ResponseCookie accessCookie = ResponseCookie.from("access_token", "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/")
                .maxAge(0)
                .build();

        ResponseCookie refreshCookie = ResponseCookie.from("refresh_token", "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/api/users/refresh")
                .maxAge(0)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
    }

    private String resolveRefreshToken(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies())
                .filter(c -> "refresh_token".equals(c.getName()))
                .map(Cookie::getValue)
                .filter(v -> v != null && !v.isBlank())
                .findFirst()
                .orElse(null);
    }
}
