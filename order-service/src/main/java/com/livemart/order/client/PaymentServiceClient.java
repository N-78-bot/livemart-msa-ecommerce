package com.livemart.order.client;

import com.livemart.order.dto.PaymentRequest;
import com.livemart.order.dto.PaymentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import java.time.Duration;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceClient {
    private final WebClient.Builder webClientBuilder;

    public PaymentResponse processPayment(PaymentRequest request) {
        log.info("Calling Payment Service: orderNumber={}", request.getOrderNumber());

        return webClientBuilder.build()
                .post()
                .uri("http://localhost:8084/api/payments")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(PaymentResponse.class)
                .timeout(Duration.ofSeconds(5))
                .block();
    }

}