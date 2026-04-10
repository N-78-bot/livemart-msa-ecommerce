package com.livemart.product.event;

import com.livemart.product.domain.ProcessedEvent;
import com.livemart.product.repository.ProcessedEventRepository;
import com.livemart.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProductEventConsumer {
    private final ProductService productService;
    private final ProcessedEventRepository processedEventRepository;

    @KafkaListener(topics = "order-events", groupId = "product-service-group")
    public void handleOrderEvent(OrderEvent event) {
        String eventId = event.getOrderNumber() + "_" + event.getEventType();

        log.info("Received order event: eventType={}, orderNumber={}, eventId={}",
                event.getEventType(), event.getOrderNumber(), eventId);

        if (processedEventRepository.existsById(eventId)) {
            log.warn("Duplicate event skipped: eventId={}", eventId);
            return;
        }

        try {
            switch (event.getEventType()) {
                case ORDER_CREATED:
                    handleOrderCreated(event, eventId);
                    break;
                case ORDER_CANCELLED:
                    handleOrderCancelled(event, eventId);
                    break;
                default:
                    log.debug("Unhandled event type: {}", event.getEventType());
            }
        } catch (Exception e) {
            log.error("Failed to process order event: orderNumber={}, eventType={}",
                    event.getOrderNumber(), event.getEventType(), e);
        }
    }

    /**
     * ORDER_CREATED: 재고 차감
     * 멀티 아이템 부분 실패 시 이미 차감된 재고 롤백 (보상 트랜잭션)
     */
    @Transactional
    protected void handleOrderCreated(OrderEvent event, String eventId) {
        log.info("Processing ORDER_CREATED: orderNumber={}, items={}",
                event.getOrderNumber(), event.getItems().size());

        List<OrderEvent.OrderItemInfo> deducted = new ArrayList<>();

        for (OrderEvent.OrderItemInfo item : event.getItems()) {
            try {
                int currentStock = productService.getProduct(item.getProductId()).getStockQuantity();
                int newStock = currentStock - item.getQuantity();
                if (newStock < 0) {
                    throw new IllegalStateException(
                        "Insufficient stock: productId=" + item.getProductId() +
                        ", available=" + currentStock + ", required=" + item.getQuantity());
                }
                productService.updateStock(item.getProductId(), newStock);
                deducted.add(item);
                log.info("Stock deducted: productId={}, quantity={}, newStock={}",
                        item.getProductId(), item.getQuantity(), newStock);
            } catch (Exception e) {
                log.error("Failed to deduct stock: productId={}, orderNumber={}. Rolling back {} items.",
                        item.getProductId(), event.getOrderNumber(), deducted.size(), e);
                // 이미 차감된 아이템 롤백 (보상 트랜잭션)
                rollbackStockDeductions(deducted, event.getOrderNumber());
                // processedEvent 저장하지 않음 → Kafka 재처리 허용
                return;
            }
        }

        saveProcessedEvent(eventId, "ORDER_CREATED");
        log.info("Stock deduction complete: orderNumber={}, items={}",
                event.getOrderNumber(), deducted.size());
    }

    /**
     * ORDER_CANCELLED: 재고 복원
     * 개별 아이템 복원 실패 시 최대 3회 재시도 (지수 백오프)
     */
    @Transactional
    protected void handleOrderCancelled(OrderEvent event, String eventId) {
        log.info("Processing ORDER_CANCELLED: orderNumber={}, reason={}",
                event.getOrderNumber(), event.getCancelReason());

        List<Long> failedProducts = new ArrayList<>();

        for (OrderEvent.OrderItemInfo item : event.getItems()) {
            boolean restored = restoreStockWithRetry(item, event.getOrderNumber(), 3);
            if (!restored) {
                failedProducts.add(item.getProductId());
            }
        }

        if (!failedProducts.isEmpty()) {
            log.error("[SAGA-ALERT] Stock restoration FAILED after retries: orderNumber={}, failedProductIds={}. " +
                      "Manual intervention required.",
                      event.getOrderNumber(), failedProducts);
            // 나머지 아이템은 성공했으므로 중복 방지를 위해 processed 기록
            // (운영팀이 failedProducts를 수동으로 복원해야 함)
        }

        saveProcessedEvent(eventId, "ORDER_CANCELLED");
        log.info("Order cancellation processed: orderNumber={}, failed={}",
                event.getOrderNumber(), failedProducts.size());
    }

    /**
     * 재고 복원 - 예외 전파 → KafkaConfig DefaultErrorHandler 지수 백오프 위임
     * Kafka 컨슈머 스레드를 직접 블로킹하지 않음
     */
    private boolean restoreStockWithRetry(OrderEvent.OrderItemInfo item, String orderNumber, int maxRetries) {
        try {
            productService.restoreStock(item.getProductId(), item.getQuantity());
            log.info("재고 복원 성공: productId={}, quantity={}", item.getProductId(), item.getQuantity());
            return true;
        } catch (Exception e) {
            log.warn("재고 복원 실패 (Kafka 에러 핸들러 재시도 위임): productId={}, orderNumber={}",
                    item.getProductId(), orderNumber, e);
            throw e; // DefaultErrorHandler에 위임
        }
    }

    /**
     * 부분 차감 롤백 - ORDER_CREATED 실패 시 이미 차감된 재고 복원
     */
    private void rollbackStockDeductions(List<OrderEvent.OrderItemInfo> deducted, String orderNumber) {
        for (OrderEvent.OrderItemInfo item : deducted) {
            try {
                productService.restoreStock(item.getProductId(), item.getQuantity());
                log.info("Rollback stock restored: productId={}, quantity={}",
                        item.getProductId(), item.getQuantity());
            } catch (Exception rollbackEx) {
                log.error("[SAGA-ALERT] Rollback FAILED: productId={}, orderNumber={}. " +
                          "Stock inconsistency detected! Manual fix required.",
                          item.getProductId(), orderNumber, rollbackEx);
            }
        }
    }

    private void saveProcessedEvent(String eventId, String eventType) {
        processedEventRepository.save(ProcessedEvent.builder()
                .eventId(eventId)
                .eventType(eventType)
                .processedAt(LocalDateTime.now())
                .build());
    }
}
