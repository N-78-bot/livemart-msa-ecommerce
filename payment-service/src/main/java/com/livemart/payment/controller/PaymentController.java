package com.livemart.payment.controller;

import com.livemart.common.audit.AuditLog;
import com.livemart.common.idempotency.IdempotencyKey;
import com.livemart.payment.client.OrderFeignClient;
import com.livemart.payment.dto.*;
import com.livemart.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payment", description = "결제 관리 API")
public class PaymentController {

    private final PaymentService paymentService;
    private final OrderFeignClient orderFeignClient;

    @PostMapping
    @IdempotencyKey(prefix = "payment-process")
    @AuditLog(action = "PROCESS_PAYMENT", resource = "Payment")
    @Operation(summary = "결제 처리")
    // 내부 서비스 호출 허용 (인증은 api-gateway가 처리)
    public ResponseEntity<PaymentResponse> processPayment(@Valid @RequestBody PaymentRequest.Create request) {
        // 서버 측 주문 금액 검증 — 클라이언트 금액 조작 방어
        try {
            OrderInfo order = orderFeignClient.getOrderByNumber(request.getOrderNumber());

            if (order.getTotalAmount() != null && order.getTotalAmount().compareTo(request.getAmount()) != 0) {
                log.warn("결제 금액 불일치 감지 - orderNumber: {}, 요청: {}, 실제: {}",
                        request.getOrderNumber(), request.getAmount(), order.getTotalAmount());
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "결제 금액이 일치하지 않습니다");
            }

            if ("PAID".equals(order.getStatus()) || "PAYMENT_COMPLETED".equals(order.getStatus())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 결제 완료된 주문입니다");
            }
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            // order-service 호출 실패 시 결제 진행 차단 (Fail-Closed)
            log.error("주문 정보 조회 실패 - orderNumber: {}, error: {}", request.getOrderNumber(), e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "주문 정보를 확인할 수 없습니다. 잠시 후 다시 시도해주세요.");
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(paymentService.processPayment(request));
    }

    @PostMapping("/refund")
    @IdempotencyKey(prefix = "payment-refund")
    @AuditLog(action = "REFUND_PAYMENT", resource = "Payment")
    @Operation(summary = "환불 처리")
    // 내부 서비스 호출 허용 (인증은 api-gateway가 처리)
    public ResponseEntity<PaymentResponse> refund(@Valid @RequestBody PaymentRequest.Refund request) {
        return ResponseEntity.ok(paymentService.refundPayment(request));
    }

    @GetMapping("/transaction/{transactionId}")
    @Operation(summary = "거래 ID로 결제 조회")
    public ResponseEntity<PaymentResponse> getByTransaction(
            @PathVariable String transactionId,
            @RequestHeader(value = "X-User-Id", required = false) String requestingUserId,
            @RequestHeader(value = "X-User-Role", required = false) String requestingUserRole) {
        PaymentResponse response = paymentService.getByTransactionId(transactionId);
        validatePaymentAccess(response.getUserId(), requestingUserId, requestingUserRole);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/order/{orderNumber}")
    @Operation(summary = "주문번호로 결제 조회")
    public ResponseEntity<PaymentResponse> getByOrder(
            @PathVariable String orderNumber,
            @RequestHeader(value = "X-User-Id", required = false) String requestingUserId,
            @RequestHeader(value = "X-User-Role", required = false) String requestingUserRole) {
        PaymentResponse response = paymentService.getByOrderNumber(orderNumber);
        validatePaymentAccess(response.getUserId(), requestingUserId, requestingUserRole);
        return ResponseEntity.ok(response);
    }

    /** 결제 소유자 또는 관리자만 접근 허용 */
    private void validatePaymentAccess(Long paymentUserId, String requestingUserId, String requestingUserRole) {
        if (requestingUserId == null) return; // 내부 서비스 호출은 헤더 없이 통과
        boolean isAdmin = "ADMIN".equals(requestingUserRole);
        boolean isOwner = requestingUserId.equals(String.valueOf(paymentUserId));
        if (!isOwner && !isAdmin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "결제 정보 접근 권한이 없습니다");
        }
    }

    /**
     * Toss Payments 결제 승인
     * 프론트에서 Toss 위젯 결제 완료 후 paymentKey/orderId/amount로 최종 승인 요청
     */
    @PostMapping("/toss/confirm")
    @Operation(summary = "Toss Payments 결제 승인")
    // 내부 서비스 호출 허용 (인증은 api-gateway가 처리)
    public ResponseEntity<PaymentResponse> confirmToss(
            @Valid @RequestBody PaymentRequest.TossConfirm request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(paymentService.confirmTossPayment(request));
    }
}
