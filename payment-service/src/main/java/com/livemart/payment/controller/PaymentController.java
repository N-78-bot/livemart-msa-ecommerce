package com.livemart.payment.controller;

import com.livemart.payment.dto.PaymentRequest;
import com.livemart.payment.dto.PaymentResponse;
import com.livemart.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<PaymentResponse> processPayment(@RequestBody PaymentRequest request) {
        return ResponseEntity.ok(paymentService.processPayment(request));
    }

    @PostMapping("/{transactionId}/cancel")
    public ResponseEntity<PaymentResponse> cancelPayment(@PathVariable String transactionId) {
        return ResponseEntity.ok(paymentService.cancelPayment(transactionId));
    }

    @GetMapping("/order/{orderNumber}")
    public ResponseEntity<PaymentResponse> getPaymentByOrderNumber(@PathVariable String orderNumber) {
        return ResponseEntity.ok(paymentService.getPaymentByOrderNumber(orderNumber));
    }
}