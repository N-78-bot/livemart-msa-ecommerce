package com.livemart.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.livemart.payment.domain.*;
import com.livemart.payment.dto.*;
import com.livemart.payment.event.PaymentEvent;
import com.livemart.payment.repository.*;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PaymentService 종합 테스트")
class PaymentServiceComprehensiveTest {

    private PaymentService paymentService;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentEventRepository eventRepository;

    @Mock
    @SuppressWarnings("unchecked")
    private KafkaTemplate<String, PaymentEvent> kafkaTemplate;

    @Mock
    private TossPaymentClient tossPaymentClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        // Optional<ObjectMapper>를 직접 주입하여 NPE 방지
        paymentService = new PaymentService(paymentRepository, eventRepository, kafkaTemplate,
                Optional.of(objectMapper), tossPaymentClient);
    }

    // ──────────────────────────────────────────────────────────────────
    // initiatePayment (= processPayment) tests
    // ──────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("결제 시작 (initiatePayment)")
    class InitiatePaymentTest {

        @Test
        @DisplayName("성공 - 결제 생성 후 COMPLETED 상태 반환")
        void initiatePayment_success() {
            // given
            PaymentRequest.Create request = PaymentRequest.Create.builder()
                    .orderNumber("ORD-2024-001")
                    .userId(1L)
                    .amount(new BigDecimal("75000"))
                    .paymentMethod(PaymentMethod.CREDIT_CARD)
                    .build();

            given(paymentRepository.save(any(Payment.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));
            given(eventRepository.save(any(com.livemart.payment.domain.PaymentEvent.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            // when
            PaymentResponse response = paymentService.processPayment(request);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getOrderNumber()).isEqualTo("ORD-2024-001");
            assertThat(response.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
            assertThat(response.getTransactionId()).startsWith("TXN-");
            assertThat(response.getAmount()).isEqualByComparingTo(new BigDecimal("75000"));
            assertThat(response.getApprovalNumber()).startsWith("APR-");
            then(paymentRepository).should(times(1)).save(any(Payment.class));
            then(kafkaTemplate).should(atLeastOnce()).send(eq("payment-events"), anyString(), any(PaymentEvent.class));
        }

        @Test
        @DisplayName("성공 - KAKAO_PAY 결제 수단 사용")
        void initiatePayment_kakaoPay_success() {
            // given
            PaymentRequest.Create request = PaymentRequest.Create.builder()
                    .orderNumber("ORD-2024-002")
                    .userId(2L)
                    .amount(new BigDecimal("30000"))
                    .paymentMethod(PaymentMethod.KAKAO_PAY)
                    .build();

            given(paymentRepository.save(any(Payment.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));
            given(eventRepository.save(any(com.livemart.payment.domain.PaymentEvent.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            // when
            PaymentResponse response = paymentService.processPayment(request);

            // then
            assertThat(response.getPaymentMethod()).isEqualTo(PaymentMethod.KAKAO_PAY);
            assertThat(response.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        }

        @Test
        @DisplayName("결제 저장 실패 시 FAILED 상태 반환")
        void initiatePayment_saveFails_returnsFailed() {
            // given: first save call (during processing) throws, simulating payment gateway failure
            PaymentRequest.Create request = PaymentRequest.Create.builder()
                    .orderNumber("ORD-2024-003")
                    .userId(3L)
                    .amount(new BigDecimal("50000"))
                    .paymentMethod(PaymentMethod.NAVER_PAY)
                    .build();

            // Simulate failure: the second save (in the catch block) returns the failed payment
            given(paymentRepository.save(any(Payment.class)))
                    .willThrow(new RuntimeException("PG 연결 실패"))
                    .willAnswer(invocation -> invocation.getArgument(0));

            // when
            PaymentResponse response = paymentService.processPayment(request);

            // then
            assertThat(response.getStatus()).isEqualTo(PaymentStatus.FAILED);
            // save called twice: once in try block (fails), once in catch block
            then(paymentRepository).should(times(2)).save(any(Payment.class));
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // processPaymentCallback tests (maps to getByOrderNumber + refundPayment)
    // ──────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("결제 콜백 처리 (processPaymentCallback)")
    class ProcessPaymentCallbackTest {

        @Test
        @DisplayName("PAYMENT_SUCCESS 콜백 - 완료된 결제 조회 성공")
        void processPaymentCallback_success() {
            // given: a completed payment exists
            Payment payment = Payment.builder()
                    .transactionId("TXN-ABCD1234")
                    .orderNumber("ORD-2024-010")
                    .userId(10L)
                    .amount(new BigDecimal("100000"))
                    .paymentMethod(PaymentMethod.CREDIT_CARD)
                    .status(PaymentStatus.COMPLETED)
                    .approvalNumber("APR-XYZ789")
                    .build();

            given(paymentRepository.findByOrderNumber("ORD-2024-010")).willReturn(Optional.of(payment));

            // when: callback queries completed payment by order number
            PaymentResponse response = paymentService.getByOrderNumber("ORD-2024-010");

            // then
            assertThat(response).isNotNull();
            assertThat(response.getOrderNumber()).isEqualTo("ORD-2024-010");
            assertThat(response.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
            assertThat(response.getApprovalNumber()).isEqualTo("APR-XYZ789");
            assertThat(response.getTransactionId()).isEqualTo("TXN-ABCD1234");
        }

        @Test
        @DisplayName("멱등성 검사 - 이미 처리된 결제는 중복 처리하지 않음")
        void processPaymentCallback_alreadyProcessed() {
            // given: payment is already COMPLETED (idempotency: second callback for same order)
            Payment payment = Payment.builder()
                    .transactionId("TXN-IDEM5678")
                    .orderNumber("ORD-2024-011")
                    .userId(11L)
                    .amount(new BigDecimal("55000"))
                    .paymentMethod(PaymentMethod.KAKAO_PAY)
                    .status(PaymentStatus.COMPLETED)
                    .build();

            given(paymentRepository.findByOrderNumber("ORD-2024-011")).willReturn(Optional.of(payment));

            // when: callback queries by order number again (duplicate call)
            PaymentResponse first = paymentService.getByOrderNumber("ORD-2024-011");
            PaymentResponse second = paymentService.getByOrderNumber("ORD-2024-011");

            // then: both responses are identical (no re-processing)
            assertThat(first.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
            assertThat(second.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
            assertThat(first.getTransactionId()).isEqualTo(second.getTransactionId());
            // repository was queried twice but no save was triggered
            then(paymentRepository).should(never()).save(any(Payment.class));
        }

        @Test
        @DisplayName("PAYMENT_FAIL 콜백 - 실패 상태 결제 조회")
        void processPaymentCallback_failure() {
            // given: a failed payment
            Payment failedPayment = Payment.builder()
                    .transactionId("TXN-FAIL9999")
                    .orderNumber("ORD-2024-012")
                    .userId(12L)
                    .amount(new BigDecimal("80000"))
                    .paymentMethod(PaymentMethod.CREDIT_CARD)
                    .status(PaymentStatus.FAILED)
                    .build();

            given(paymentRepository.findByOrderNumber("ORD-2024-012")).willReturn(Optional.of(failedPayment));

            // when
            PaymentResponse response = paymentService.getByOrderNumber("ORD-2024-012");

            // then
            assertThat(response.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(response.getOrderNumber()).isEqualTo("ORD-2024-012");
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // getPaymentByOrderNumber tests
    // ──────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("주문번호로 결제 조회 (getPaymentByOrderNumber)")
    class GetPaymentByOrderNumberTest {

        @Test
        @DisplayName("성공 - 주문번호로 결제 정보 조회")
        void getPaymentByOrderNumber_success() {
            // given
            Payment payment = Payment.builder()
                    .transactionId("TXN-ORDER100")
                    .orderNumber("ORD-2024-100")
                    .userId(5L)
                    .amount(new BigDecimal("45000"))
                    .paymentMethod(PaymentMethod.NAVER_PAY)
                    .status(PaymentStatus.COMPLETED)
                    .build();

            given(paymentRepository.findByOrderNumber("ORD-2024-100")).willReturn(Optional.of(payment));

            // when
            PaymentResponse response = paymentService.getByOrderNumber("ORD-2024-100");

            // then
            assertThat(response).isNotNull();
            assertThat(response.getOrderNumber()).isEqualTo("ORD-2024-100");
            assertThat(response.getAmount()).isEqualByComparingTo(new BigDecimal("45000"));
            assertThat(response.getPaymentMethod()).isEqualTo(PaymentMethod.NAVER_PAY);
            then(paymentRepository).should().findByOrderNumber("ORD-2024-100");
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 주문번호")
        void getPaymentByOrderNumber_notFound() {
            // given
            given(paymentRepository.findByOrderNumber("ORD-NONEXISTENT")).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> paymentService.getByOrderNumber("ORD-NONEXISTENT"))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("ORD-NONEXISTENT");
        }

        @Test
        @DisplayName("성공 - 거래ID로 결제 정보 조회")
        void getPaymentByTransactionId_success() {
            // given
            Payment payment = Payment.builder()
                    .transactionId("TXN-LOOKUP01")
                    .orderNumber("ORD-2024-200")
                    .userId(7L)
                    .amount(new BigDecimal("25000"))
                    .paymentMethod(PaymentMethod.CREDIT_CARD)
                    .status(PaymentStatus.COMPLETED)
                    .build();

            given(paymentRepository.findByTransactionId("TXN-LOOKUP01")).willReturn(Optional.of(payment));

            // when
            PaymentResponse response = paymentService.getByTransactionId("TXN-LOOKUP01");

            // then
            assertThat(response.getTransactionId()).isEqualTo("TXN-LOOKUP01");
            assertThat(response.getOrderNumber()).isEqualTo("ORD-2024-200");
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 거래ID")
        void getPaymentByTransactionId_notFound() {
            // given
            given(paymentRepository.findByTransactionId("TXN-INVALID")).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> paymentService.getByTransactionId("TXN-INVALID"))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // Refund/cancellation tests
    // ──────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("환불 처리")
    class RefundTest {

        @Test
        @DisplayName("전액 환불 - CANCELLED 상태 전환")
        void refund_fullAmount_success() {
            // given
            Payment payment = Payment.builder()
                    .transactionId("TXN-REFUND01")
                    .orderNumber("ORD-2024-300")
                    .userId(20L)
                    .amount(new BigDecimal("60000"))
                    .paymentMethod(PaymentMethod.CREDIT_CARD)
                    .status(PaymentStatus.COMPLETED)
                    .build();

            given(paymentRepository.findByTransactionId("TXN-REFUND01")).willReturn(Optional.of(payment));
            given(paymentRepository.save(any(Payment.class))).willAnswer(inv -> inv.getArgument(0));
            given(eventRepository.save(any(com.livemart.payment.domain.PaymentEvent.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            PaymentRequest.Refund request = PaymentRequest.Refund.builder()
                    .transactionId("TXN-REFUND01")
                    .reason("고객 변심")
                    .build();

            // when
            PaymentResponse response = paymentService.refundPayment(request);

            // then
            assertThat(response.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
            assertThat(response.getRefundedAmount()).isEqualByComparingTo(new BigDecimal("60000"));
        }

        @Test
        @DisplayName("부분 환불 - PARTIALLY_REFUNDED 상태 전환")
        void refund_partialAmount_success() {
            // given
            Payment payment = Payment.builder()
                    .transactionId("TXN-PARTIAL01")
                    .orderNumber("ORD-2024-301")
                    .userId(21L)
                    .amount(new BigDecimal("90000"))
                    .paymentMethod(PaymentMethod.NAVER_PAY)
                    .status(PaymentStatus.COMPLETED)
                    .build();

            given(paymentRepository.findByTransactionId("TXN-PARTIAL01")).willReturn(Optional.of(payment));
            given(paymentRepository.save(any(Payment.class))).willAnswer(inv -> inv.getArgument(0));
            given(eventRepository.save(any(com.livemart.payment.domain.PaymentEvent.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            PaymentRequest.Refund request = PaymentRequest.Refund.builder()
                    .transactionId("TXN-PARTIAL01")
                    .amount(new BigDecimal("30000"))
                    .reason("일부 상품 반품")
                    .build();

            // when
            PaymentResponse response = paymentService.refundPayment(request);

            // then
            assertThat(response.getStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
            assertThat(response.getRefundedAmount()).isEqualByComparingTo(new BigDecimal("30000"));
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 거래 환불 시 EntityNotFoundException")
        void refund_notFound_throwsException() {
            // given
            given(paymentRepository.findByTransactionId("TXN-GHOST")).willReturn(Optional.empty());

            PaymentRequest.Refund request = PaymentRequest.Refund.builder()
                    .transactionId("TXN-GHOST")
                    .build();

            // when & then
            assertThatThrownBy(() -> paymentService.refundPayment(request))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("TXN-GHOST");
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // Kafka event publishing verification
    // ──────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Kafka 이벤트 발행 검증")
    class KafkaEventPublishingTest {

        @Test
        @DisplayName("결제 성공 시 PAYMENT_COMPLETED 이벤트 발행")
        void processPayment_publishesCompletedEvent() {
            // given
            PaymentRequest.Create request = PaymentRequest.Create.builder()
                    .orderNumber("ORD-KAFKA-001")
                    .userId(1L)
                    .amount(new BigDecimal("20000"))
                    .paymentMethod(PaymentMethod.KAKAO_PAY)
                    .build();

            given(paymentRepository.save(any(Payment.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));
            given(eventRepository.save(any(com.livemart.payment.domain.PaymentEvent.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            // when
            paymentService.processPayment(request);

            // then
            then(kafkaTemplate).should(atLeastOnce())
                    .send(eq("payment-events"), eq("ORD-KAFKA-001"), any(PaymentEvent.class));
        }

        @Test
        @DisplayName("환불 성공 시 PAYMENT_CANCELLED 이벤트 발행")
        void refundPayment_publishesCancelledEvent() {
            // given
            Payment payment = Payment.builder()
                    .transactionId("TXN-EVT001")
                    .orderNumber("ORD-KAFKA-002")
                    .userId(2L)
                    .amount(new BigDecimal("35000"))
                    .paymentMethod(PaymentMethod.CREDIT_CARD)
                    .status(PaymentStatus.COMPLETED)
                    .build();

            given(paymentRepository.findByTransactionId("TXN-EVT001")).willReturn(Optional.of(payment));
            given(paymentRepository.save(any(Payment.class))).willAnswer(inv -> inv.getArgument(0));
            given(eventRepository.save(any(com.livemart.payment.domain.PaymentEvent.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            PaymentRequest.Refund refundRequest = PaymentRequest.Refund.builder()
                    .transactionId("TXN-EVT001")
                    .reason("환불 요청")
                    .build();

            // when
            paymentService.refundPayment(refundRequest);

            // then
            then(kafkaTemplate).should(atLeastOnce())
                    .send(eq("payment-events"), eq("ORD-KAFKA-002"), any(PaymentEvent.class));
        }
    }
}
