package com.livemart.user.service;

import com.livemart.user.domain.User;
import com.livemart.user.domain.UserRole;
import com.livemart.user.domain.UserStatus;
import com.livemart.user.dto.*;

import java.util.List;
import java.util.stream.Collectors;
import com.livemart.user.repository.UserRepository;
import com.livemart.user.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public UserResponse signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다");
        }

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .name(request.getName())
                .phoneNumber(request.getPhoneNumber())
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();

        User savedUser = userRepository.save(user);
        log.info("User registered: userId={}, email={}", savedUser.getId(), savedUser.getEmail());
        return UserResponse.from(savedUser);
    }

    /**
     * 로그인 — 토큰은 httpOnly 쿠키로만 전달하므로 LoginResult에 포함하지만
     * Controller에서 쿠키에만 설정하고 body에는 사용자 정보만 반환
     */
    public LoginResult login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다");
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new IllegalStateException("비활성화된 계정입니다");
        }

        String accessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getEmail(), user.getRole().name());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId());

        log.info("User logged in: userId={}, role={}", user.getId(), user.getRole());

        return LoginResult.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .accessTokenExpiry(jwtTokenProvider.getAccessTokenExpiration())
                .refreshTokenExpiry(jwtTokenProvider.getRefreshTokenExpiration())
                .userId(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .role(user.getRole().name())
                .build();
    }

    /**
     * 리프레시 토큰으로 새 토큰 발급 — 쿠키에서 읽어 문자열로 전달
     */
    public LoginResult refresh(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new IllegalArgumentException("유효하지 않은 Refresh Token입니다");
        }

        Long userId = jwtTokenProvider.getUserIdFromToken(refreshToken);

        if (!jwtTokenProvider.validateRefreshToken(userId, refreshToken)) {
            throw new IllegalArgumentException("Refresh Token이 만료되었거나 일치하지 않습니다");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다"));

        String newAccessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getEmail(), user.getRole().name());
        String newRefreshToken = jwtTokenProvider.createRefreshToken(user.getId());

        log.info("Token refreshed: userId={}", userId);

        return LoginResult.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .accessTokenExpiry(jwtTokenProvider.getAccessTokenExpiration())
                .refreshTokenExpiry(jwtTokenProvider.getRefreshTokenExpiration())
                .userId(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .role(user.getRole().name())
                .build();
    }

    public void logout(Long userId) {
        jwtTokenProvider.deleteRefreshToken(userId);
        log.info("User logged out: userId={}", userId);
    }

    public UserResponse getUserById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다"));
        return UserResponse.from(user);
    }

    // ─── 내 정보 수정 ────────────────────────────────────────────

    @Transactional
    public UserResponse updateMyProfile(Long userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다"));
        String newName = (request.getName() != null && !request.getName().isBlank())
                ? request.getName() : user.getName();
        user.updateProfile(newName, request.getPhoneNumber());
        log.info("Profile updated: userId={}", userId);
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다"));

        if (user.isOAuthUser()) {
            throw new IllegalStateException("소셜 로그인 계정은 비밀번호를 변경할 수 없습니다");
        }

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new IllegalArgumentException("현재 비밀번호가 올바르지 않습니다");
        }

        user.updatePassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        log.info("Password changed: userId={}", userId);
    }

    // ─── 관리자 전용 ──────────────────────────────────────────────

    public Page<UserResponse> getAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable).map(UserResponse::from);
    }

    public long getUserCount() {
        return userRepository.count();
    }

    @Transactional
    public UserResponse updateUserRole(Long userId, String roleName) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다"));
        UserRole newRole = UserRole.valueOf(roleName.toUpperCase());
        user.updateRole(newRole);
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public void deactivateUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다"));
        user.deactivate();
        userRepository.save(user);
        log.info("User deactivated by admin: userId={}", userId);
    }
}
