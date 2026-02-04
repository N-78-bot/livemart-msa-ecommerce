package com.livemart.payment.event;

import com.livemart.payment.domain.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentEvent {
    private String eventType;
    private String transactionId;
    private String orderNumber;
    private Long userId;
    private BigDecimal amount;
    private PaymentStatus status;
    private String approvalNumber;
    private LocalDateTime occurredAt;
}