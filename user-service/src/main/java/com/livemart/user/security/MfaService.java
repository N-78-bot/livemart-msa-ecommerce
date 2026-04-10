package com.livemart.user.security;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import dev.samstevens.totp.code.*;
import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.Base64;

/**
 * 다단계 인증 (MFA/2FA) 서비스
 *
 * TOTP (Time-based One-Time Password) 알고리즘 사용
 * - Google Authenticator 호환
 * - 30초마다 새로운 코드 생성
 * - RFC 6238 표준 준수
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MfaService {

    private static final String ISSUER = "LiveMart";
    private static final int TIME_STEP = 30; // 30초
    private static final int DIGITS = 6;     // 6자리 코드

    private final SecretGenerator secretGenerator = new dev.samstevens.totp.secret.DefaultSecretGenerator();
    private final TimeProvider timeProvider = new SystemTimeProvider();
    private final CodeGenerator codeGenerator = new DefaultCodeGenerator(HashingAlgorithm.SHA1, DIGITS);
    private final CodeVerifier codeVerifier = new DefaultCodeVerifier(codeGenerator, timeProvider);

    /**
     * MFA Secret Key 생성
     * 사용자 등록 시 한 번만 생성
     */
    public String generateSecretKey() {
        String secret = secretGenerator.generate();
        log.info("MFA secret key generated");
        return secret;
    }

    /**
     * QR Code 생성 (Base64)
     * 사용자가 Google Authenticator 앱으로 스캔
     */
    public String generateQrCodeBase64(String username, String secretKey) {
        try {
            QrData qrData = new QrData.Builder()
                .label(username)
                .secret(secretKey)
                .issuer(ISSUER)
                .algorithm(HashingAlgorithm.SHA1)
                .digits(DIGITS)
                .period(TIME_STEP)
                .build();

            String otpAuthUrl = qrData.getUri();

            // QR Code 생성
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(otpAuthUrl, BarcodeFormat.QR_CODE, 250, 250);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);

            byte[] qrCodeBytes = outputStream.toByteArray();
            String base64QrCode = Base64.getEncoder().encodeToString(qrCodeBytes);

            log.info("QR code generated for user: {}", username);

            return base64QrCode;

        } catch (Exception e) {
            log.error("Failed to generate QR code", e);
            throw new IllegalStateException("QR code generation failed", e);
        }
    }

    /**
     * TOTP 코드 검증
     * 사용자가 입력한 6자리 코드 검증
     */
    public boolean verifyCode(String secretKey, String code) {
        if (code == null || code.length() != DIGITS) {
            log.warn("Invalid code length: {}", code != null ? code.length() : "null");
            return false;
        }

        // 시간 윈도우 허용 (±1 time step = 30초 전후)
        boolean isValid = codeVerifier.isValidCode(secretKey, code);

        if (isValid) {
            log.info("MFA code verified successfully");
        } else {
            log.warn("MFA code verification failed");
        }

        return isValid;
    }

    /**
     * 현재 TOTP 코드 생성 (테스트용)
     */
    public String getCurrentCode(String secretKey) {
        try {
            long counter = Math.floorDiv(timeProvider.getTime(), TIME_STEP);
            String code = codeGenerator.generate(secretKey, counter);
            return code;
        } catch (Exception e) {
            log.error("Failed to generate code", e);
            throw new IllegalStateException("Code generation failed", e);
        }
    }

    /**
     * Backup Codes 생성 (8개)
     * MFA 디바이스 분실 시 사용
     */
    public java.util.List<String> generateBackupCodes() {
        java.util.List<String> backupCodes = new java.util.ArrayList<>();
        java.security.SecureRandom random = new java.security.SecureRandom();

        for (int i = 0; i < 8; i++) {
            // 8자리 랜덤 코드
            String code = String.format("%08d", random.nextInt(100_000_000));
            backupCodes.add(code);
        }

        log.info("Generated {} backup codes", backupCodes.size());
        return backupCodes;
    }

    /**
     * Backup Code 검증
     */
    public boolean verifyBackupCode(String providedCode, java.util.Set<String> storedBackupCodes) {
        boolean isValid = storedBackupCodes.contains(providedCode);

        if (isValid) {
            log.info("Backup code verified and consumed");
        } else {
            log.warn("Invalid backup code provided");
        }

        return isValid;
    }

    /**
     * MFA 설정 완료 여부 확인
     */
    public boolean isMfaSetupComplete(String secretKey) {
        return secretKey != null && !secretKey.isEmpty();
    }

    /**
     * MFA 설정 정보 생성
     */
    public MfaSetupInfo setupMfa(String username) {
        String secretKey = generateSecretKey();
        String qrCodeBase64 = generateQrCodeBase64(username, secretKey);
        java.util.List<String> backupCodes = generateBackupCodes();

        return new MfaSetupInfo(
            secretKey,
            qrCodeBase64,
            backupCodes,
            ISSUER,
            username
        );
    }

    // DTOs

    public record MfaSetupInfo(
        String secretKey,
        String qrCodeBase64,
        java.util.List<String> backupCodes,
        String issuer,
        String username
    ) {
        public String getManualEntryKey() {
            // Secret key를 4자리씩 끊어서 표시
            StringBuilder formatted = new StringBuilder();
            for (int i = 0; i < secretKey.length(); i += 4) {
                if (i > 0) formatted.append(" ");
                formatted.append(secretKey.substring(i, Math.min(i + 4, secretKey.length())));
            }
            return formatted.toString();
        }
    }

    public record MfaVerificationRequest(
        String username,
        String code
    ) {}

    public record MfaVerificationResponse(
        boolean verified,
        String message
    ) {}
}
