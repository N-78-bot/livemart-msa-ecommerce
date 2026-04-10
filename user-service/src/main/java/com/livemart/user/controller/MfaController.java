package com.livemart.user.controller;

import com.livemart.user.domain.User;
import com.livemart.user.repository.UserRepository;
import com.livemart.user.security.MfaService;
import com.livemart.user.security.MfaService.MfaSetupInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

/**
 * MFA (Multi-Factor Authentication) API
 */
@RestController
@RequestMapping("/api/v1/mfa")
@RequiredArgsConstructor
public class MfaController {

    private final MfaService mfaService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * MFA 설정 시작
     * QR 코드 및 백업 코드 생성
     */
    @PostMapping("/setup")
    public ResponseEntity<MfaSetupInfo> setupMfa(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (user.isMfaEnabled()) {
            return ResponseEntity.badRequest().build();
        }

        MfaSetupInfo setupInfo = mfaService.setupMfa(user.getEmail());

        return ResponseEntity.ok(setupInfo);
    }

    /**
     * MFA 활성화 (QR 코드 스캔 후 검증)
     */
    @PostMapping("/enable")
    public ResponseEntity<MfaEnableResponse> enableMfa(
            Authentication authentication,
            @RequestBody MfaEnableRequest request) {

        Long userId = (Long) authentication.getPrincipal();

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // 코드 검증
        boolean isValid = mfaService.verifyCode(request.secretKey(), request.verificationCode());

        if (!isValid) {
            return ResponseEntity.ok(new MfaEnableResponse(false, "Invalid verification code"));
        }

        // MFA 활성화
        user.enableMfa(request.secretKey(), request.backupCodes());
        userRepository.save(user);

        return ResponseEntity.ok(new MfaEnableResponse(true, "MFA enabled successfully"));
    }

    /**
     * MFA 비활성화
     * 보안: 현재 비밀번호 + TOTP 코드 이중 검증 필수
     */
    @PostMapping("/disable")
    public ResponseEntity<MfaDisableResponse> disableMfa(
            Authentication authentication,
            @RequestBody MfaDisableRequest request) {

        Long userId = (Long) authentication.getPrincipal();

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!user.isMfaEnabled()) {
            return ResponseEntity.badRequest().build();
        }

        // 현재 비밀번호 검증 (계정 탈취 방지)
        if (request.currentPassword() == null || request.currentPassword().isBlank()) {
            return ResponseEntity.ok(new MfaDisableResponse(false, "Current password is required"));
        }
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            return ResponseEntity.ok(new MfaDisableResponse(false, "Invalid password"));
        }

        // TOTP 코드 검증
        boolean isValid = mfaService.verifyCode(user.getMfaSecretKey(), request.verificationCode());

        if (!isValid) {
            return ResponseEntity.ok(new MfaDisableResponse(false, "Invalid verification code"));
        }

        // MFA 비활성화
        user.disableMfa();
        userRepository.save(user);

        return ResponseEntity.ok(new MfaDisableResponse(true, "MFA disabled successfully"));
    }

    /**
     * MFA 코드 검증 (로그인 시)
     */
    @PostMapping("/verify")
    public ResponseEntity<MfaVerificationResponse> verifyMfa(
            @RequestBody MfaVerificationRequest request) {

        User user = userRepository.findByEmail(request.username())
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!user.isMfaEnabled()) {
            return ResponseEntity.ok(new MfaVerificationResponse(false, "MFA not enabled"));
        }

        // TOTP 코드 검증
        boolean isValid = mfaService.verifyCode(user.getMfaSecretKey(), request.code());

        if (isValid) {
            return ResponseEntity.ok(new MfaVerificationResponse(true, "MFA verified"));
        }

        // Backup 코드 검증
        if (user.getBackupCodesSet().contains(request.code())) {
            user.consumeBackupCode(request.code());
            userRepository.save(user);
            return ResponseEntity.ok(new MfaVerificationResponse(true, "Backup code verified"));
        }

        return ResponseEntity.ok(new MfaVerificationResponse(false, "Invalid code"));
    }

    /**
     * MFA 상태 조회
     */
    @GetMapping("/status")
    public ResponseEntity<MfaStatusResponse> getMfaStatus(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        int remainingBackupCodes = user.getBackupCodesSet().size();

        return ResponseEntity.ok(new MfaStatusResponse(
            user.isMfaEnabled(),
            remainingBackupCodes
        ));
    }

    /**
     * 백업 코드 재생성
     */
    @PostMapping("/backup-codes/regenerate")
    public ResponseEntity<BackupCodesResponse> regenerateBackupCodes(
            Authentication authentication,
            @RequestBody RegenerateBackupCodesRequest request) {

        Long userId = (Long) authentication.getPrincipal();

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!user.isMfaEnabled()) {
            return ResponseEntity.badRequest().build();
        }

        // 코드 검증
        boolean isValid = mfaService.verifyCode(user.getMfaSecretKey(), request.verificationCode());

        if (!isValid) {
            return ResponseEntity.ok(new BackupCodesResponse(null, "Invalid verification code"));
        }

        // 새 백업 코드 생성
        java.util.List<String> newBackupCodes = mfaService.generateBackupCodes();
        user.enableMfa(user.getMfaSecretKey(), newBackupCodes);
        userRepository.save(user);

        return ResponseEntity.ok(new BackupCodesResponse(newBackupCodes, "Backup codes regenerated"));
    }

    // DTOs

    public record MfaEnableRequest(
        String secretKey,
        String verificationCode,
        java.util.List<String> backupCodes
    ) {}

    public record MfaEnableResponse(
        boolean success,
        String message
    ) {}

    public record MfaDisableRequest(
        String currentPassword,
        String verificationCode
    ) {}

    public record MfaDisableResponse(
        boolean success,
        String message
    ) {}

    public record MfaVerificationRequest(
        String username,
        String code
    ) {}

    public record MfaVerificationResponse(
        boolean verified,
        String message
    ) {}

    public record MfaStatusResponse(
        boolean enabled,
        int remainingBackupCodes
    ) {}

    public record RegenerateBackupCodesRequest(
        String verificationCode
    ) {}

    public record BackupCodesResponse(
        java.util.List<String> backupCodes,
        String message
    ) {}
}
