package com.livemart.order.service;

import com.livemart.order.client.ProductFeignClient;
import com.livemart.order.dto.OrderItemRequest;
import com.livemart.order.dto.ProductInfo;
import com.livemart.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.StructuredTaskScope;

/**
 * Java 21 Structured Concurrency 기반 병렬 상품 검증 서비스 (JEP 453)
 *
 * [기존 문제]
 * OrderService.createOrder()에서 상품 정보를 순차 조회 (N개 상품 = N번 API 호출, 직렬)
 * → 3개 상품 주문 시: 300ms × 3 = 900ms
 *
 * [개선]
 * StructuredTaskScope.ShutdownOnFailure를 사용한 병렬 조회
 * → 3개 상품 주문 시: max(300ms) = 300ms (3배 빠름)
 *
 * [안전성 보장]
 * - ShutdownOnFailure: 하나라도 실패하면 나머지 즉시 취소 (리소스 낭비 방지)
 * - scope.join() 호출 전까지 모든 subtask가 완료되거나 취소됨
 * - Virtual Thread에서 실행되므로 블로킹 I/O도 효율적
 * - scope.throwIfFailed()로 예외를 호출 스레드로 정확히 전파
 *
 * [Java 21 Preview → Java 23 Final]
 * JEP 453 (Java 21 Preview) → JEP 462 (Java 22 Preview) → Final in Java 23
 * 현재 프로젝트 Java 21 기준: --enable-preview 필요 (build.gradle에 설정됨)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ParallelProductValidationService {

    private final ProductFeignClient productFeignClient;

    /**
     * N개 상품을 병렬로 조회하고 재고를 검증합니다.
     *
     * @param items 주문 항목 목록
     * @return 검증된 상품 정보 목록 (items와 동일한 순서 보장)
     * @throws BusinessException 재고 부족 시
     * @throws RuntimeException 상품 서비스 호출 실패 시
     */
    public List<ProductInfo> validateInParallel(List<OrderItemRequest> items) {
        if (items.isEmpty()) return List.of();

        // 단일 상품은 오버헤드 없이 직접 조회
        if (items.size() == 1) {
            return List.of(validateSingleProduct(items.get(0)));
        }

        log.debug("Starting parallel product validation: count={}", items.size());
        long startMs = System.currentTimeMillis();

        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {

            // 모든 상품 조회를 Virtual Thread에서 병렬 실행
            List<StructuredTaskScope.Subtask<ProductInfo>> subtasks = items.stream()
                    .map(item -> scope.fork(() -> validateSingleProduct(item)))
                    .toList();

            // 모든 subtask 완료 대기 (또는 하나라도 실패 시 나머지 취소)
            scope.join().throwIfFailed();

            List<ProductInfo> results = subtasks.stream()
                    .map(StructuredTaskScope.Subtask::get)
                    .toList();

            long elapsed = System.currentTimeMillis() - startMs;
            log.info("Parallel product validation completed: count={}, elapsedMs={}", items.size(), elapsed);

            return results;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("VALIDATION_INTERRUPTED", "상품 검증 중 인터럽트 발생", 503);
        } catch (ExecutionException e) {
            // subtask에서 발생한 예외 (재고 부족 BusinessException 포함) 언랩
            Throwable cause = e.getCause();
            if (cause instanceof BusinessException be) throw be;
            if (cause instanceof RuntimeException re) throw re;
            throw new BusinessException("VALIDATION_FAILED", "상품 검증 중 오류 발생", 500);
        }
    }

    private ProductInfo validateSingleProduct(OrderItemRequest item) {
        ProductInfo product = productFeignClient.getProductWithLock(item.getProductId());

        if (product.getStockQuantity() < item.getQuantity()) {
            log.warn("Insufficient stock: productId={}, required={}, available={}",
                    item.getProductId(), item.getQuantity(), product.getStockQuantity());
            throw BusinessException.insufficientStock(product.getId());
        }

        log.debug("Product validated: productId={}, name={}, stock={}",
                product.getId(), product.getName(), product.getStockQuantity());
        return product;
    }
}
