package com.livemart.payment.service;

import com.livemart.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Toss Payments REST API 클라이언트
 * 공식 문서: https://docs.tosspayments.com/reference
 *
 * - 커넥션 타임아웃: 3초
 * - 읽기 타임아웃: 10초
 * - 4xx: BusinessException.paymentFailed (클라이언트 오류)
 * - 5xx: BusinessException.paymentFailed (Toss 서버 장애)
 * - 네트워크: BusinessException.paymentFailed (연결 실패)
 */
@Slf4j
@Component
public class TossPaymentClient {

    private static final String TOSS_API_BASE = "https://api.tosspayments.com/v1/payments";
    private static final int CONNECT_TIMEOUT_MS = 3_000;
    private static final int READ_TIMEOUT_MS    = 10_000;

    @Value("${toss.payments.secret-key:test_sk_zXLkKEypNArWmo50nX3lmeaxYG5R}")
    private String secretKey;

    private final RestTemplate restTemplate;

    public TossPaymentClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * 결제 승인 (paymentKey + orderId + amount 검증 후 승인)
     */
    public Map<String, Object> confirm(String paymentKey, String orderId, Long amount) {
        HttpHeaders headers = buildHeaders();
        Map<String, Object> body = new HashMap<>();
        body.put("paymentKey", paymentKey);
        body.put("orderId", orderId);
        body.put("amount", amount);

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    TOSS_API_BASE + "/confirm",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    new ParameterizedTypeReference<>() {}
            );
            log.info("Toss 결제 승인 성공: orderId={}, amount={}", orderId, amount);
            return response.getBody();
        } catch (HttpClientErrorException e) {
            log.error("Toss 결제 승인 4xx: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw BusinessException.paymentFailed("Toss 결제 승인 실패: " + e.getResponseBodyAsString());
        } catch (HttpServerErrorException e) {
            log.error("Toss 결제 승인 5xx: status={}", e.getStatusCode());
            throw BusinessException.paymentFailed("Toss 결제 서버 일시 오류. 잠시 후 다시 시도해주세요.");
        } catch (ResourceAccessException e) {
            log.error("Toss 결제 승인 네트워크 오류: {}", e.getMessage());
            throw BusinessException.paymentFailed("결제 서버 연결 실패. 잠시 후 다시 시도해주세요.");
        }
    }

    /**
     * 결제 취소 (환불)
     */
    public Map<String, Object> cancel(String paymentKey, String cancelReason, Long cancelAmount) {
        HttpHeaders headers = buildHeaders();
        Map<String, Object> body = new HashMap<>();
        body.put("cancelReason", cancelReason);
        if (cancelAmount != null) {
            body.put("cancelAmount", cancelAmount);
        }

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    TOSS_API_BASE + "/" + paymentKey + "/cancel",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    new ParameterizedTypeReference<>() {}
            );
            log.info("Toss 결제 취소 성공: paymentKey={}", paymentKey);
            return response.getBody();
        } catch (HttpClientErrorException e) {
            log.error("Toss 결제 취소 4xx: {}", e.getResponseBodyAsString());
            throw BusinessException.paymentFailed("Toss 결제 취소 실패: " + e.getResponseBodyAsString());
        } catch (HttpServerErrorException e) {
            log.error("Toss 결제 취소 5xx: status={}", e.getStatusCode());
            throw BusinessException.paymentFailed("Toss 결제 서버 일시 오류. 잠시 후 다시 시도해주세요.");
        } catch (ResourceAccessException e) {
            log.error("Toss 결제 취소 네트워크 오류: {}", e.getMessage());
            throw BusinessException.paymentFailed("결제 서버 연결 실패. 잠시 후 다시 시도해주세요.");
        }
    }

    private HttpHeaders buildHeaders() {
        String encoded = Base64.getEncoder().encodeToString((secretKey + ":").getBytes());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Basic " + encoded);
        return headers;
    }
}
