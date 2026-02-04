package com.livemart.payment.dto;

import com.livemart.payment.domain.PaymentStatus;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class PaymentResponse {
    private Long id;
    private String transactionId;
    private String orderNumber;
    private BigDecimal amount;
    private PaymentStatus status;
    private String approvalNumber;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}