package com.livemart.product.event;

import com.livemart.product.domain.ProductStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductEvent {

    private EventType eventType;
    private Long productId;
    private String productName;
    private BigDecimal price;
    private Integer stockQuantity;
    private ProductStatus status;
    private Long sellerId;
    private LocalDateTime occurredAt;

    public enum EventType {
        CREATED,
        UPDATED,
        DELETED,
        STOCK_CHANGED
    }
}