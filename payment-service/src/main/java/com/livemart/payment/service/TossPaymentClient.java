package com.livemart.payment.service;

import com.livemart.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Toss Payments REST API 클라이언트
 * 공식 문서: https://docs.tosspayments.com/reference
 */
@Slf4j
@Component
public class TossPaymentClient {

    private static final String TOSS_API_BASE = "https://api.tosspayments.com/v1/payments";

    @Value("${toss.payments.secret-key:test_sk_zXLkKEypNArWmo50nX3lmeaxYG5R}")
    private String secretKey;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 결제 승인 (paymentKey + orderId + amount 검증 후 승인)
     */
    public Map<String, Object> confirm(String paymentKey, String orderId, Long amount) {
        String encoded = Base64.getEncoder().encodeToString((secretKey + ":").getBytes());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Basic " + encoded);

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
            log.error("Toss 결제 승인 실패: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw BusinessException.paymentFailed("Toss 결제 승인 실패: " + e.getResponseBodyAsString());
        }
    }

    /**
     * 결제 취소 (환불)
     */
    public Map<String, Object> cancel(String paymentKey, String cancelReason, Long cancelAmount) {
        String encoded = Base64.getEncoder().encodeToString((secretKey + ":").getBytes());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Basic " + encoded);

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
            log.error("Toss 결제 취소 실패: {}", e.getResponseBodyAsString());
            throw BusinessException.paymentFailed("Toss 결제 취소 실패: " + e.getResponseBodyAsString());
        }
    }
}
