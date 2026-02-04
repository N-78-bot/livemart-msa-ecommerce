package com.livemart.order.client;

import com.livemart.order.dto.ProductInfo;
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
public class ProductServiceClient {
    private final WebClient.Builder webClientBuilder;

    @CircuitBreaker(name = "productService", fallbackMethod = "getProductFallback")
    public ProductInfo getProduct(Long productId) {
        log.info("Calling Product Service for productId: {}", productId);

        return webClientBuilder.build()
                .get()
                .uri("http://localhost:8082/api/products/" + productId)
                .retrieve()
                .bodyToMono(ProductInfo.class)
                .timeout(Duration.ofSeconds(3))
                .block();
    }

    @CircuitBreaker(name = "productService", fallbackMethod = "updateStockFallback")
    public void updateStock(Long productId, int stockQuantity) {
        log.info("Calling Product Service for productId: {}", productId);

        Map<String, Integer> body = Map.of("stockQuantity", stockQuantity);

        webClientBuilder.build()
                .patch()
                .uri("http://localhost:8082/api/products/" + productId + "/stock")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Void.class)
                .timeout(Duration.ofSeconds(10))
                .block();

        log.info("Updating stock for productId: {}, quantity: {}", productId, stockQuantity);
    }

    private ProductInfo getProductFallback(Long productId, Exception e) {
        log.error("Failed to get product: productId={}", productId, e);
        throw new RuntimeException("상품 정보를 가져올 수 없습니다.");
    }

    private void updateStockFallback(Long productId, int stockQuantity, Exception e) {
        log.error("Failed to update stock for productId: {}", productId, e);
        throw new RuntimeException("재고 업데이트에 실패했습니다.");
    }
}