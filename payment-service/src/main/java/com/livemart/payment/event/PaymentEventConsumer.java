package com.livemart.payment.event;

import com.livemart.payment.domain.PaymentStatus;
import com.livemart.payment.dto.PaymentRequest;
import com.livemart.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Kafka Consumer: order-events 수신
 * - ORDER_CANCELLED → 결제 취소(환불) 처리 (최대 3회 재시도)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventConsumer {

    private final PaymentService paymentService;

    private static final int MAX_REFUND_RETRIES = 3;

    @KafkaListener(
        topics = "order-events",
        groupId = "payment-service-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleOrderEvent(
            @Payload Map<String, Object> event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {

        try {
            String eventType = String.valueOf(event.get("eventType"));
            String orderNumber = String.valueOf(event.get("orderNumber"));

            log.debug("주문 이벤트 수신: type={}, order={}", eventType, orderNumber);

            switch (eventType) {
                case "ORDER_CANCELLED" -> handleOrderCancelled(orderNumber, event);
                case "ORDER_CREATED"   -> log.debug("주문 생성 이벤트 수신 (처리 없음): order={}", orderNumber);
                default                -> log.debug("처리하지 않는 이벤트 타입: {}", eventType);
            }
        } catch (Exception e) {
            log.error("주문 이벤트 처리 실패: event={}", event, e);
        }
    }

    /**
     * 주문 취소 → 결제 완료 상태인 경우 자동 환불
     * 환불 실패 시 최대 3회 재시도 (지수 백오프)
     */
    private void handleOrderCancelled(String orderNumber, Map<String, Object> event) {
        try {
            var paymentResp = paymentService.getByOrderNumber(orderNumber);

            if (paymentResp.getStatus() != PaymentStatus.COMPLETED &&
                paymentResp.getStatus() != PaymentStatus.PARTIALLY_REFUNDED) {
                log.info("환불 불필요 - 결제 상태: order={}, status={}", orderNumber, paymentResp.getStatus());
                return;
            }

            String transactionId = paymentResp.getTransactionId();
            boolean refunded = refundWithRetry(orderNumber, transactionId);

            if (!refunded) {
                log.error("[SAGA-ALERT] 자동 환불 최종 실패: order={}, txn={}. " +
                          "운영팀 수동 환불 처리 필요. amount={}, status={}",
                          orderNumber, transactionId,
                          paymentResp.getAmount(), paymentResp.getStatus());
            }

        } catch (jakarta.persistence.EntityNotFoundException e) {
            log.debug("결제 정보 없음 (결제 미완료 주문 취소): order={}", orderNumber);
        } catch (Exception e) {
            log.error("주문 취소 이벤트 처리 중 오류: order={}", orderNumber, e);
        }
    }

    /**
     * 환불 처리 - 예외 전파 → KafkaConfig DefaultErrorHandler 지수 백오프 위임
     * Kafka 컨슈머 스레드를 직접 블로킹하지 않음
     */
    private boolean refundWithRetry(String orderNumber, String transactionId) {
        try {
            PaymentRequest.Refund refundReq = new PaymentRequest.Refund();
            refundReq.setTransactionId(transactionId);
            paymentService.refundPayment(refundReq);
            log.info("자동 환불 성공: order={}, txn={}", orderNumber, transactionId);
            return true;
        } catch (Exception e) {
            log.warn("환불 처리 실패 (Kafka 에러 핸들러 재시도 위임): order={}, txn={}", orderNumber, transactionId, e);
            throw e; // DefaultErrorHandler에 위임
        }
    }
}
