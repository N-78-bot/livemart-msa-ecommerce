package com.livemart.product.inventory;

import com.livemart.product.domain.Product;
import com.livemart.product.domain.ReplenishmentOrderEntity;
import com.livemart.product.repository.ProductRepository;
import com.livemart.product.repository.ReplenishmentOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.security.SecureRandom;
import java.util.Optional;

/**
 * 재고 발주 관리 서비스
 * 발주 생성, 추적, 완료 처리 — JPA 기반 영속 저장소 사용
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReplenishmentOrderService {

    private final ProductRepository productRepository;
    private final ReplenishmentOrderRepository replenishmentOrderRepository;

    /**
     * 발주 생성
     */
    @Transactional
    public ReplenishmentOrder createOrder(Long productId, int quantity, int leadTimeDays) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));

        LocalDateTime expectedArrival = LocalDateTime.now().plusDays(leadTimeDays);

        ReplenishmentOrderEntity entity = ReplenishmentOrderEntity.builder()
            .productId(productId)
            .productName(product.getName())
            .quantity(quantity)
            .totalCost(product.getPrice().multiply(BigDecimal.valueOf(quantity)))
            .status(ReplenishmentOrderEntity.OrderStatus.PENDING)
            .expectedArrival(expectedArrival)
            .leadTimeDays(leadTimeDays)
            .trackingNumber(generateTrackingNumber(productId))
            .build();

        ReplenishmentOrderEntity saved = replenishmentOrderRepository.save(entity);

        log.info("Replenishment order created: orderId={}, productId={}, quantity={}, expectedArrival={}",
                 saved.getId(), productId, quantity, expectedArrival);

        return toDto(saved);
    }

    /**
     * 발주 상태 업데이트
     */
    @Transactional
    public ReplenishmentOrder updateOrderStatus(Long orderId, OrderStatus newStatus) {
        ReplenishmentOrderEntity entity = replenishmentOrderRepository.findById(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        entity.updateStatus(toEntityStatus(newStatus));
        ReplenishmentOrderEntity saved = replenishmentOrderRepository.save(entity);

        log.info("Order status updated: orderId={}, status={}", orderId, newStatus);

        if (newStatus == OrderStatus.RECEIVED) {
            receiveOrder(toDto(saved));
        }

        return toDto(saved);
    }

    /**
     * 발주 물품 입고 처리
     */
    @Transactional
    public void receiveOrder(ReplenishmentOrder order) {
        Product product = productRepository.findById(order.productId())
            .orElseThrow(() -> new IllegalArgumentException("Product not found: " + order.productId()));

        int currentStock = product.getStockQuantity();
        int newStock = currentStock + order.quantity();

        product.updateStock(newStock);
        productRepository.save(product);

        log.info("Replenishment order received: productId={}, addedQuantity={}, currentStock={}, newStock={}",
                 order.productId(), order.quantity(), currentStock, newStock);
    }

    /**
     * 발주 취소
     */
    @Transactional
    public ReplenishmentOrder cancelOrder(Long orderId, String reason) {
        ReplenishmentOrderEntity entity = replenishmentOrderRepository.findById(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        if (entity.getStatus() == ReplenishmentOrderEntity.OrderStatus.RECEIVED) {
            throw new IllegalStateException("Cannot cancel received order");
        }

        entity.updateStatus(ReplenishmentOrderEntity.OrderStatus.CANCELLED);
        ReplenishmentOrderEntity saved = replenishmentOrderRepository.save(entity);

        log.info("Order cancelled: orderId={}, reason={}", orderId, reason);

        return toDto(saved);
    }

    /**
     * 발주 조회
     */
    @Transactional(readOnly = true)
    public Optional<ReplenishmentOrder> getOrder(Long orderId) {
        return replenishmentOrderRepository.findById(orderId).map(this::toDto);
    }

    /**
     * 제품별 발주 내역 조회
     */
    @Transactional(readOnly = true)
    public List<ReplenishmentOrder> getOrdersByProduct(Long productId) {
        return replenishmentOrderRepository
            .findByProductIdOrderByOrderDateDesc(productId)
            .stream().map(this::toDto).toList();
    }

    /**
     * 상태별 발주 조회
     */
    @Transactional(readOnly = true)
    public List<ReplenishmentOrder> getOrdersByStatus(OrderStatus status) {
        return replenishmentOrderRepository
            .findByStatusOrderByOrderDateDesc(toEntityStatus(status))
            .stream().map(this::toDto).toList();
    }

    private static final List<ReplenishmentOrderEntity.OrderStatus> ACTIVE_STATUSES =
            List.of(ReplenishmentOrderEntity.OrderStatus.PENDING, ReplenishmentOrderEntity.OrderStatus.IN_TRANSIT);

    /**
     * 지연된 발주 조회 (예상 도착일 초과)
     */
    @Transactional(readOnly = true)
    public List<ReplenishmentOrder> getDelayedOrders() {
        return replenishmentOrderRepository.findDelayedOrders(ACTIVE_STATUSES, LocalDateTime.now())
            .stream().map(this::toDto).toList();
    }

    /**
     * 발주 통계 조회
     */
    @Transactional(readOnly = true)
    public OrderStatistics getOrderStatistics() {
        long totalOrders     = replenishmentOrderRepository.count();
        long pendingOrders   = replenishmentOrderRepository.countByStatus(ReplenishmentOrderEntity.OrderStatus.PENDING);
        long inTransitOrders = replenishmentOrderRepository.countByStatus(ReplenishmentOrderEntity.OrderStatus.IN_TRANSIT);
        long receivedOrders  = replenishmentOrderRepository.countByStatus(ReplenishmentOrderEntity.OrderStatus.RECEIVED);
        long cancelledOrders = replenishmentOrderRepository.countByStatus(ReplenishmentOrderEntity.OrderStatus.CANCELLED);
        long delayedOrders   = replenishmentOrderRepository.findDelayedOrders(ACTIVE_STATUSES, LocalDateTime.now()).size();

        BigDecimal totalCostBd = replenishmentOrderRepository.sumTotalCostByStatus(ReplenishmentOrderEntity.OrderStatus.RECEIVED);
        double totalCost = totalCostBd != null ? totalCostBd.doubleValue() : 0.0;

        // 평균 리드타임: DB에서 계산하지 않고 Java에서 집계 (이식성)
        double averageLeadTime = replenishmentOrderRepository
            .findByStatusOrderByOrderDateDesc(ReplenishmentOrderEntity.OrderStatus.RECEIVED)
            .stream()
            .filter(e -> e.getActualArrival() != null)
            .mapToLong(e -> java.time.Duration.between(e.getOrderDate(), e.getActualArrival()).toDays())
            .average()
            .orElse(0.0);

        return new OrderStatistics(
            totalOrders, pendingOrders, inTransitOrders,
            receivedOrders, cancelledOrders, delayedOrders,
            totalCost, averageLeadTime
        );
    }

    // ── 변환 헬퍼 ──────────────────────────────────────────────────────

    private ReplenishmentOrder toDto(ReplenishmentOrderEntity e) {
        return new ReplenishmentOrder(
            e.getId(),
            e.getProductId(),
            e.getProductName(),
            e.getQuantity(),
            e.getTotalCost(),
            toDtoStatus(e.getStatus()),
            e.getOrderDate(),
            e.getExpectedArrival(),
            e.getLeadTimeDays(),
            e.getActualArrival(),
            e.getTrackingNumber()
        );
    }

    private OrderStatus toDtoStatus(ReplenishmentOrderEntity.OrderStatus s) {
        return switch (s) {
            case PENDING    -> OrderStatus.PENDING;
            case IN_TRANSIT -> OrderStatus.IN_TRANSIT;
            case RECEIVED   -> OrderStatus.RECEIVED;
            case CANCELLED  -> OrderStatus.CANCELLED;
        };
    }

    private ReplenishmentOrderEntity.OrderStatus toEntityStatus(OrderStatus s) {
        return switch (s) {
            case PENDING    -> ReplenishmentOrderEntity.OrderStatus.PENDING;
            case IN_TRANSIT -> ReplenishmentOrderEntity.OrderStatus.IN_TRANSIT;
            case RECEIVED   -> ReplenishmentOrderEntity.OrderStatus.RECEIVED;
            case CANCELLED  -> ReplenishmentOrderEntity.OrderStatus.CANCELLED;
        };
    }

    private String generateTrackingNumber(Long productId) {
        return String.format("REP-%d-%d-%d",
            productId,
            System.currentTimeMillis(),
            new SecureRandom().nextInt(1000));
    }

    // ── 공개 타입 (하위 호환 유지) ──────────────────────────────────────

    public enum OrderStatus {
        PENDING("발주 대기"),
        IN_TRANSIT("배송 중"),
        RECEIVED("입고 완료"),
        CANCELLED("발주 취소");

        private final String description;

        OrderStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    public record ReplenishmentOrder(
        Long id,
        Long productId,
        String productName,
        int quantity,
        BigDecimal totalCost,
        OrderStatus status,
        LocalDateTime orderDate,
        LocalDateTime expectedArrival,
        int leadTimeDays,
        LocalDateTime actualArrival,
        String trackingNumber
    ) {
        public boolean isDelayed() {
            if (status == OrderStatus.RECEIVED || status == OrderStatus.CANCELLED) {
                return false;
            }
            return expectedArrival.isBefore(LocalDateTime.now());
        }

        public long getDaysUntilArrival() {
            return java.time.Duration.between(LocalDateTime.now(), expectedArrival).toDays();
        }

        public long getActualLeadTime() {
            if (actualArrival == null) {
                return 0;
            }
            return java.time.Duration.between(orderDate, actualArrival).toDays();
        }
    }

    public record OrderStatistics(
        long totalOrders,
        long pendingOrders,
        long inTransitOrders,
        long receivedOrders,
        long cancelledOrders,
        long delayedOrders,
        double totalCost,
        double averageLeadTime
    ) {}
}
