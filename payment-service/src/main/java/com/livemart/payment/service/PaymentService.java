package com.livemart.payment.service;

import com.livemart.payment.domain.Payment;
import com.livemart.payment.domain.PaymentStatus;
import com.livemart.payment.dto.PaymentRequest;
import com.livemart.payment.dto.PaymentResponse;
import com.livemart.payment.event.PaymentEvent;
import com.livemart.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final KafkaTemplate<String, PaymentEvent> kafkaTemplate;

    public PaymentResponse processPayment(PaymentRequest request) {
        String transactionId = UUID.randomUUID().toString();

        Payment payment = Payment.builder()
                .transactionId(transactionId)
                .orderNumber(request.getOrderNumber())
                .userId(request.getUserId())
                .amount(request.getAmount())
                .method(request.getMethod())
                .cardNumber(maskCardNumber(request.getCardNumber()))
                .status(PaymentStatus.PENDING)
                .build();

        boolean success = simulatePayment(request);

        if (success) {
            String approvalNumber = generateApprovalNumber();
            payment.complete(approvalNumber);
            log.info("결제 성공: transactionId={}, approvalNumber={}", transactionId, approvalNumber);
            publishPaymentEvent(payment, "PAYMENT_COMPLETED");
        } else {
            payment.fail("결제 승인 실패");
            log.error("결제 실패: transactionId={}", transactionId);
            publishPaymentEvent(payment, "PAYMENT_FAILED");
        }

        paymentRepository.save(payment);
        return toResponse(payment);
    }

    @Transactional
    public void cancelPayment(String transactionId, String reason) {
        Payment payment = paymentRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new RuntimeException("결제를 찾을 수 없습니다."));

        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            throw new RuntimeException("취소할 수 없는 결제 상태입니다.");
        }

        payment.cancel(reason);
        paymentRepository.save(payment);

        // Kafka 이벤트 발행
        PaymentEvent event = PaymentEvent.builder()
                .eventType(PaymentEvent.EventType.PAYMENT_CANCELLED)
                .transactionId(payment.getTransactionId())
                .orderNumber(payment.getOrderNumber())
                .amount(payment.getAmount())
                .occurredAt(LocalDateTime.now())
                .build();

        kafkaTemplate.send("payment-events", payment.getTransactionId(), event);

        log.info("Payment cancelled: transactionId={}, reason={}", transactionId, reason);
    }

    public PaymentResponse cancelPayment(String transactionId) {
        Payment payment = paymentRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new RuntimeException("결제 정보를 찾을 수 없습니다."));

        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            throw new RuntimeException("취소할 수 없는 상태입니다.");
        }

        payment.cancel();
        log.info("결제 취소: transactionId={}", transactionId);
        publishPaymentEvent(payment, "PAYMENT_CANCELLED");
        return toResponse(payment);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentByOrderNumber(String orderNumber) {
        Payment payment = paymentRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new RuntimeException("결제 정보를 찾을 수 없습니다."));
        return toResponse(payment);
    }

    private void publishPaymentEvent(Payment payment, String eventType) {
        PaymentEvent event = PaymentEvent.builder()
                .eventType(PaymentEvent.EventType.valueOf(eventType))
                .transactionId(payment.getTransactionId())
                .orderNumber(payment.getOrderNumber())
                .userId(payment.getUserId())
                .amount(payment.getAmount())
                .status(payment.getStatus())
                .approvalNumber(payment.getApprovalNumber())
                .occurredAt(LocalDateTime.now())
                .build();

        kafkaTemplate.send("payment-events", payment.getTransactionId(), event);
        log.info("Kafka 이벤트 발행: eventType={}, transactionId={}", eventType, payment.getTransactionId());
    }

    private boolean simulatePayment(PaymentRequest request) {
        return true;
    }

    private String generateApprovalNumber() {
        return "APV-" + System.currentTimeMillis();
    }

    private String maskCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 4) return "****";
        return "****-****-****-" + cardNumber.substring(cardNumber.length() - 4);
    }

    private PaymentResponse toResponse(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .transactionId(payment.getTransactionId())
                .orderNumber(payment.getOrderNumber())
                .amount(payment.getAmount())
                .status(payment.getStatus())
                .approvalNumber(payment.getApprovalNumber())
                .createdAt(payment.getCreatedAt())
                .completedAt(payment.getCompletedAt())
                .build();
    }
}