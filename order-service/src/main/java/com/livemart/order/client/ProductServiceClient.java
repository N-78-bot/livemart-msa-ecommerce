package com.livemart.order.client;

import com.livemart.common.exception.BusinessException;
import com.livemart.order.dto.ProductInfo;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProductServiceClient {

    private final RestTemplate restTemplate;
    private static final String PRODUCT_SERVICE_URL = "http://PRODUCT-SERVICE/api/products";

    @CircuitBreaker(name = "productService", fallbackMethod = "getProductFallback")
    @Retry(name = "productService")
    public ProductInfo getProduct(Long productId) {
        log.info("Calling Product Service for productId: {}", productId);
        String url = PRODUCT_SERVICE_URL + "/" + productId;
        return restTemplate.getForObject(url, ProductInfo.class);
    }

    @CircuitBreaker(name = "productService", fallbackMethod = "getProductFallback")
    @Retry(name = "productService")
    public ProductInfo getProductWithLock(Long productId) {
        log.info("Calling Product Service WITH LOCK for productId: {}", productId);
        String url = PRODUCT_SERVICE_URL + "/" + productId + "/with-lock";
        return restTemplate.getForObject(url, ProductInfo.class);
    }

    @CircuitBreaker(name = "productService", fallbackMethod = "updateStockFallback")
    @Retry(name = "productService")
    public void updateStock(Long productId, int newStock) {
        log.info("Updating stock for productId: {} to {}", productId, newStock);
        String url = PRODUCT_SERVICE_URL + "/" + productId + "/stock";
        StockUpdateRequest request = new StockUpdateRequest(newStock);
        restTemplate.patchForObject(url, request, Void.class);
    }

    @CircuitBreaker(name = "productService", fallbackMethod = "restoreStockFallback")
    @Retry(name = "productService")
    public void restoreStock(Long productId, int quantity) {
        log.info("Restoring stock for productId: {} by {}", productId, quantity);
        ProductInfo product = getProduct(productId);
        int newStock = product.getStockQuantity() + quantity;
        updateStock(productId, newStock);
    }

    // Fallback methods
    private ProductInfo getProductFallback(Long productId, Exception e) {
        log.error("Product Service unavailable: productId={}", productId, e);
        throw new BusinessException("SERVICE_UNAVAILABLE", "상품 서비스가 일시적으로 이용 불가합니다. 잠시 후 다시 시도해주세요.", 503);
    }

    private void updateStockFallback(Long productId, int newStock, Exception e) {
        log.error("Product Service stock update failed: productId={}", productId, e);
        throw new BusinessException("SERVICE_UNAVAILABLE", "재고 업데이트가 실패했습니다. 잠시 후 다시 시도해주세요.", 503);
    }

    private void restoreStockFallback(Long productId, int quantity, Exception e) {
        log.error("Product Service stock restore failed: productId={}", productId, e);
        throw new BusinessException("SERVICE_UNAVAILABLE", "재고 복구가 실패했습니다. 잠시 후 다시 시도해주세요.", 503);
    }

    public record StockUpdateRequest(Integer stockQuantity) {}
}
