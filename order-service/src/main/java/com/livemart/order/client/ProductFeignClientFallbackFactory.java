package com.livemart.order.client;

import com.livemart.common.exception.BusinessException;
import com.livemart.order.dto.ProductInfo;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ProductFeignClientFallbackFactory implements FallbackFactory<ProductFeignClient> {

    @Override
    public ProductFeignClient create(Throwable cause) {
        return new ProductFeignClient() {
            @Override
            public ProductInfo getProduct(Long productId) {
                return handleError("getProduct", productId, cause);
            }

            @Override
            public ProductInfo getProductWithLock(Long productId) {
                return handleError("getProductWithLock", productId, cause);
            }

            @Override
            public void updateStock(Long productId, Integer stockQuantity) {
                if (cause instanceof FeignException fe) {
                    int status = fe.status();
                    if (status == 400 || status == 404) {
                        throw BusinessException.notFound("Product", productId);
                    }
                }
                log.error("Product Service 호출 실패 (updateStock): productId={}", productId, cause);
                throw new BusinessException("SERVICE_UNAVAILABLE", "재고 업데이트가 실패했습니다.", 503);
            }
        };
    }

    private ProductInfo handleError(String method, Long productId, Throwable cause) {
        if (cause instanceof FeignException fe) {
            int status = fe.status();
            if (status == 400 || status == 404) {
                // 상품 없음 — 서비스 장애 아님
                throw BusinessException.notFound("Product", productId);
            }
        }
        log.error("Product Service 호출 실패 ({}): productId={}", method, productId, cause);
        throw new BusinessException("SERVICE_UNAVAILABLE", "상품 서비스가 일시적으로 이용 불가합니다.", 503);
    }
}
