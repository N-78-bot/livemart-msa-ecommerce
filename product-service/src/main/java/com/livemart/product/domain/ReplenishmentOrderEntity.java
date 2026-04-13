package com.livemart.product.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 재고 발주 JPA 엔티티
 * 서비스 재시작 후에도 발주 데이터 유지 (인메모리 Map 대체)
 */
@Entity
@Table(name = "replenishment_orders", indexes = {
        @Index(name = "idx_replenishment_product", columnList = "product_id"),
        @Index(name = "idx_replenishment_status", columnList = "status")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ReplenishmentOrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "total_cost", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalCost;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @CreationTimestamp
    @Column(name = "order_date", nullable = false, updatable = false)
    private LocalDateTime orderDate;

    @Column(name = "expected_arrival", nullable = false)
    private LocalDateTime expectedArrival;

    @Column(name = "lead_time_days", nullable = false)
    private Integer leadTimeDays;

    @Column(name = "actual_arrival")
    private LocalDateTime actualArrival;

    @Column(name = "tracking_number", nullable = false, length = 100)
    private String trackingNumber;

    public void updateStatus(OrderStatus newStatus) {
        this.status = newStatus;
        if (newStatus == OrderStatus.RECEIVED) {
            this.actualArrival = LocalDateTime.now();
        }
    }

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
}
