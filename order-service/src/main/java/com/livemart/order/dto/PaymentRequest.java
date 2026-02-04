package com.livemart.order.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class PaymentRequest {
    private String orderNumber;
    private Long userId;
    private BigDecimal amount;
    private String method;
    private String cardNumber;
}