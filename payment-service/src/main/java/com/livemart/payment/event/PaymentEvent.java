package com.livemart.payment.event;

import com.livemart.payment.domain.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentEvent {
    private EventType eventType;
    private String transactionId;
    private String orderNumber;
    private Long userId;
    private BigDecimal amount;
    private PaymentStatus status;
    private String approvalNumber;
    private LocalDateTime occurredAt;

    public enum EventType {
        PAYMENT_COMPLETED,
        PAYMENT_FAILED,
        PAYMENT_CANCELLED
    }
}