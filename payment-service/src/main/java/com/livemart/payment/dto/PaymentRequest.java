package com.livemart.payment.dto;

import com.livemart.payment.domain.PaymentMethod;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class PaymentRequest {
    private String orderNumber;
    private Long userId;
    private BigDecimal amount;
    private PaymentMethod method;
    private String cardNumber;
}