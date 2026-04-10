package com.livemart.product.reactive;

import com.livemart.product.dto.ProductResponse;
import com.livemart.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * WebFlux 기반 반응형 상품 조회 서비스
 * 비동기 논블로킹 I/O로 성능 향상
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReactiveProductQueryService {

    private final ProductRepository productRepository;

    /**
     * 반응형 상품 조회 (단건)
     */
    @Cacheable(value = "products", key = "#productId")
    public Mono<ProductResponse> getProductReactive(Long productId) {
        return Mono.fromCallable(() -> {
            log.info("Fetching product reactively: productId={}", productId);
            return productRepository.findById(productId)
                    .map(ProductResponse::from)
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));
        })
        .subscribeOn(Schedulers.boundedElastic())
        .timeout(Duration.ofSeconds(3))
        .doOnError(e -> log.error("Failed to fetch product: productId={}", productId, e))
        .cache(Duration.ofMinutes(5));
    }

    /**
     * 반응형 상품 목록 조회 (스트리밍)
     */
    public Flux<ProductResponse> getProductsReactive(int page, int size) {
        return Flux.defer(() -> {
            log.info("Fetching products reactively: page={}, size={}", page, size);
            return Flux.fromIterable(productRepository.findAll(PageRequest.of(page, size)).getContent());
        })
        .map(ProductResponse::from)
        .subscribeOn(Schedulers.boundedElastic())
        .timeout(Duration.ofSeconds(5))
        .doOnComplete(() -> log.info("Products streaming completed"));
    }

    /**
     * 가격 범위 기반 검색 (스트리밍)
     */
    public Flux<ProductResponse> searchByPriceRange(BigDecimal minPrice, BigDecimal maxPrice) {
        return Flux.defer(() -> {
            log.info("Searching products by price range: {} - {}", minPrice, maxPrice);
            return Flux.fromIterable(productRepository.findByPriceBetween(minPrice, maxPrice));
        })
        .map(ProductResponse::from)
        .subscribeOn(Schedulers.boundedElastic())
        .delayElements(Duration.ofMillis(10))  // 백프레셔 시뮬레이션
        .doOnNext(p -> log.debug("Streaming product: {}", p.getName()));
    }

    /**
     * 카테고리별 상품 조회 (반응형 + 병렬 처리)
     */
    public Flux<ProductResponse> getProductsByCategory(Long categoryId) {
        return Flux.defer(() -> {
            log.info("Fetching products by category reactively: categoryId={}", categoryId);
            return Flux.fromIterable(productRepository.findByCategoryIdList(categoryId));
        })
        .map(ProductResponse::from)
        .subscribeOn(Schedulers.parallel())
        .timeout(Duration.ofSeconds(5));
    }

    /**
     * 재고 부족 상품 조회 (실시간 알림용)
     */
    public Flux<ProductResponse> getLowStockProducts(Integer threshold) {
        return Flux.defer(() ->
            Flux.fromIterable(productRepository.findAll())
                .filter(p -> p.getStockQuantity() < threshold)
        )
        .map(ProductResponse::from)
        .subscribeOn(Schedulers.boundedElastic())
        .doOnNext(p -> log.warn("Low stock product: {} (stock: {})", p.getName(), p.getStockQuantity()));
    }

    /**
     * 다중 상품 조회 (Batch) - 병렬 처리
     */
    public Flux<ProductResponse> getProductsBatch(Flux<Long> productIds) {
        return productIds
            .flatMap(this::getProductReactive, 10)  // 동시에 10개씩 병렬 처리
            .onErrorContinue((e, id) -> log.error("Failed to fetch product: id={}", id, e));
    }
}
