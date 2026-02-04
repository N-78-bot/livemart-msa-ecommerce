package com.livemart.order.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PaymentResponse {
    private Long id;
    private String transactionId;
    private String orderNumber;
    private BigDecimal amount;
    private String status;
    private String approvalNumber;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}