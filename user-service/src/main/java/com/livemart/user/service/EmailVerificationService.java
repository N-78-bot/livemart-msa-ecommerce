package com.livemart.user.service;

import com.livemart.common.exception.BusinessException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private final JavaMailSender mailSender;
    private final StringRedisTemplate redisTemplate;

    @Value("${spring.mail.username}")
    private String senderEmail;

    private static final String CODE_PREFIX = "email:verify:";
    private static final long CODE_EXPIRE_MINUTES = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    /** 인증 코드 생성 후 이메일 발송, Redis에 5분 저장 */
    public void sendVerificationCode(String email) {
        String code = generateCode();
        String key = CODE_PREFIX + email;

        redisTemplate.opsForValue().set(key, code, CODE_EXPIRE_MINUTES, TimeUnit.MINUTES);

        try {
            sendEmail(email, code);
            log.info("Email verification code sent: email={}", email);
        } catch (Exception e) {
            redisTemplate.delete(key);
            log.error("Failed to send verification email: email={}, error={}", email, e.getMessage());
            throw new BusinessException("EMAIL_SEND_FAILED", "이메일 전송에 실패했습니다: " + e.getMessage(), 503);
        }
    }

    /** 코드 검증 */
    public boolean verifyCode(String email, String inputCode) {
        String key = CODE_PREFIX + email;
        String storedCode = redisTemplate.opsForValue().get(key);

        if (storedCode == null) {
            log.warn("Verification code not found or expired: email={}", email);
            return false;
        }

        boolean match = storedCode.equals(inputCode.trim());
        if (match) {
            redisTemplate.delete(key); // 인증 완료 후 삭제
            log.info("Email verification successful: email={}", email);
        } else {
            log.warn("Invalid verification code: email={}", email);
        }
        return match;
    }

    private String generateCode() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    private void sendEmail(String to, String code) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(senderEmail);
        helper.setTo(to);
        helper.setSubject("[LiveMart] 이메일 인증 코드");
        helper.setText(buildEmailHtml(code), true); // HTML 메일

        mailSender.send(message);
    }

    private String buildEmailHtml(String code) {
        return """
            <!DOCTYPE html>
            <html>
            <body style="font-family: 'Apple SD Gothic Neo', sans-serif; background:#f5f5f5; margin:0; padding:20px;">
              <div style="max-width:480px; margin:0 auto; background:#fff; border-radius:16px; overflow:hidden; box-shadow:0 4px 20px rgba(0,0,0,0.08);">
                <div style="background:#E11D48; padding:28px 32px;">
                  <h1 style="color:#fff; margin:0; font-size:22px; font-weight:900; letter-spacing:-0.5px;">
                    <span style="font-weight:400;">Live</span>Mart
                  </h1>
                </div>
                <div style="padding:32px;">
                  <h2 style="color:#111; margin:0 0 8px; font-size:20px;">이메일 인증 코드</h2>
                  <p style="color:#555; margin:0 0 24px; font-size:14px;">아래 6자리 코드를 입력해주세요. 코드는 5분간 유효합니다.</p>
                  <div style="background:#fff5f7; border:2px solid #E11D48; border-radius:12px; padding:24px; text-align:center;">
                    <span style="font-size:40px; font-weight:900; color:#E11D48; letter-spacing:8px;">%s</span>
                  </div>
                  <p style="color:#888; margin:24px 0 0; font-size:12px;">본인이 요청하지 않은 경우 이 이메일을 무시하세요.</p>
                </div>
                <div style="background:#f9f9f9; padding:16px 32px; text-align:center;">
                  <p style="color:#aaa; margin:0; font-size:12px;">© 2025 LiveMart. All rights reserved.</p>
                </div>
              </div>
            </body>
            </html>
            """.formatted(code);
    }
}
