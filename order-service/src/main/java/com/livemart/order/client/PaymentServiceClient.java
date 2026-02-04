package com.livemart.order.client;

import com.livemart.order.dto.PaymentRequest;
import com.livemart.order.dto.PaymentResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import java.time.Duration;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceClient {
    private final WebClient.Builder webClientBuilder;

    @CircuitBreaker(name = "paymentService", fallbackMethod = "processPaymentFallback")
    public PaymentResponse processPayment(PaymentRequest request) {
        log.info("Processing payment for order: {}", request.getOrderNumber());

        return webClientBuilder.build()
                .post()
                .uri("http://localhost:8084/api/payments")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(PaymentResponse.class)
                .timeout(Duration.ofSeconds(10))
                .block();
    }

    // 결제 취소 메서드 추가
    @CircuitBreaker(name = "paymentService", fallbackMethod = "cancelPaymentFallback")
    public void cancelPayment(String transactionId, String reason) {
        log.info("Cancelling payment: transactionId={}, reason={}", transactionId, reason);

        Map<String, String> body = Map.of("reason", reason);

        webClientBuilder.build()
                .post()
                .uri("http://localhost:8084/api/payments/" + transactionId + "/cancel")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Void.class)
                .timeout(Duration.ofSeconds(10))
                .block();

        log.info("Payment cancelled: {}", transactionId);
    }

    private PaymentResponse processPaymentFallback(PaymentRequest request, Exception e) {
        log.error("Failed to process payment: order={}", request.getOrderNumber(), e);
        throw new RuntimeException("결제 처리에 실패했습니다.");
    }

    private void cancelPaymentFallback(String transactionId, String reason, Exception e) {
        log.error("Failed to cancel payment: transactionId={}", transactionId, e);
        throw new RuntimeException("결제 취소에 실패했습니다.");
    }
}