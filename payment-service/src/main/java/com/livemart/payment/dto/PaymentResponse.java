package com.livemart.payment.dto;

import com.livemart.payment.domain.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PaymentResponse {
    private Long id;
    private Long userId;
    private String transactionId;
    private String orderNumber;
    private BigDecimal amount;
    private BigDecimal refundedAmount;
    private PaymentMethod paymentMethod;
    private PaymentStatus status;
    private String approvalNumber;
    private String cardLast4;
    private String cardBrand;
    private Instant createdAt;
    private Instant completedAt;

    public static PaymentResponse from(Payment p) {
        return PaymentResponse.builder()
                .id(p.getId()).userId(p.getUserId()).transactionId(p.getTransactionId())
                .orderNumber(p.getOrderNumber()).amount(p.getAmount())
                .refundedAmount(p.getRefundedAmount())
                .paymentMethod(p.getPaymentMethod()).status(p.getStatus())
                .approvalNumber(p.getApprovalNumber())
                .cardLast4(p.getCardLast4()).cardBrand(p.getCardBrand())
                .createdAt(p.getCreatedAt()).completedAt(p.getCompletedAt())
                .build();
    }
}
