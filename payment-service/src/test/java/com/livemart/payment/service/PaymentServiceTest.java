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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PaymentService 단위 테스트")
class PaymentServiceTest {

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

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(paymentRepository, eventRepository, kafkaTemplate,
                Optional.of(new ObjectMapper()), tossPaymentClient);
    }

    @Nested
    @DisplayName("결제 처리")
    class ProcessPaymentTest {

        @Test
        @DisplayName("결제 성공 시 COMPLETED 상태로 저장")
        void processPayment_success() {
            // given
            PaymentRequest.Create request = PaymentRequest.Create.builder()
                    .orderNumber("ORD-001")
                    .userId(1L)
                    .amount(new BigDecimal("50000"))
                    .paymentMethod(PaymentMethod.CREDIT_CARD)
                    .build();

            given(paymentRepository.save(any(Payment.class))).willAnswer(invocation -> {
                Payment p = invocation.getArgument(0);
                return p;
            });

            // when
            PaymentResponse response = paymentService.processPayment(request);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getOrderNumber()).isEqualTo("ORD-001");
            assertThat(response.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
            assertThat(response.getTransactionId()).startsWith("TXN-");
            assertThat(response.getAmount()).isEqualByComparingTo(new BigDecimal("50000"));
            then(paymentRepository).should(times(1)).save(any(Payment.class));
        }

        @Test
        @DisplayName("다양한 결제 수단으로 결제 가능")
        void processPayment_variousMethods() {
            for (PaymentMethod method : PaymentMethod.values()) {
                PaymentRequest.Create request = PaymentRequest.Create.builder()
                        .orderNumber("ORD-" + method.name())
                        .userId(1L)
                        .amount(new BigDecimal("10000"))
                        .paymentMethod(method)
                        .build();

                given(paymentRepository.save(any(Payment.class))).willAnswer(inv -> inv.getArgument(0));

                PaymentResponse response = paymentService.processPayment(request);
                assertThat(response.getPaymentMethod()).isEqualTo(method);
            }
        }
    }

    @Nested
    @DisplayName("환불 처리")
    class RefundPaymentTest {

        @Test
        @DisplayName("전액 환불 성공")
        void refundPayment_fullRefund() {
            // given
            Payment payment = Payment.builder()
                    .transactionId("TXN-001")
                    .orderNumber("ORD-001")
                    .userId(1L)
                    .amount(new BigDecimal("50000"))
                    .paymentMethod(PaymentMethod.CREDIT_CARD)
                    .status(PaymentStatus.COMPLETED)
                    .build();

            given(paymentRepository.findByTransactionId("TXN-001")).willReturn(Optional.of(payment));
            given(paymentRepository.save(any(Payment.class))).willAnswer(inv -> inv.getArgument(0));

            PaymentRequest.Refund request = PaymentRequest.Refund.builder()
                    .transactionId("TXN-001")
                    .reason("고객 변심")
                    .build();

            // when
            PaymentResponse response = paymentService.refundPayment(request);

            // then
            assertThat(response.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
            assertThat(response.getRefundedAmount()).isEqualByComparingTo(new BigDecimal("50000"));
        }

        @Test
        @DisplayName("부분 환불 성공")
        void refundPayment_partialRefund() {
            // given
            Payment payment = Payment.builder()
                    .transactionId("TXN-002")
                    .orderNumber("ORD-002")
                    .amount(new BigDecimal("50000"))
                    .paymentMethod(PaymentMethod.CREDIT_CARD)
                    .status(PaymentStatus.COMPLETED)
                    .build();

            given(paymentRepository.findByTransactionId("TXN-002")).willReturn(Optional.of(payment));
            given(paymentRepository.save(any(Payment.class))).willAnswer(inv -> inv.getArgument(0));

            PaymentRequest.Refund request = PaymentRequest.Refund.builder()
                    .transactionId("TXN-002")
                    .amount(new BigDecimal("20000"))
                    .reason("일부 상품 반품")
                    .build();

            // when
            PaymentResponse response = paymentService.refundPayment(request);

            // then
            assertThat(response.getStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
            assertThat(response.getRefundedAmount()).isEqualByComparingTo(new BigDecimal("20000"));
        }

        @Test
        @DisplayName("존재하지 않는 거래 환불 시 예외")
        void refundPayment_notFound() {
            given(paymentRepository.findByTransactionId("INVALID")).willReturn(Optional.empty());

            PaymentRequest.Refund request = PaymentRequest.Refund.builder()
                    .transactionId("INVALID")
                    .build();

            assertThatThrownBy(() -> paymentService.refundPayment(request))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("결제 조회")
    class GetPaymentTest {

        @Test
        @DisplayName("거래 ID로 조회 성공")
        void getByTransactionId_success() {
            Payment payment = Payment.builder()
                    .transactionId("TXN-003")
                    .orderNumber("ORD-003")
                    .amount(new BigDecimal("30000"))
                    .paymentMethod(PaymentMethod.KAKAO_PAY)
                    .status(PaymentStatus.COMPLETED)
                    .build();

            given(paymentRepository.findByTransactionId("TXN-003")).willReturn(Optional.of(payment));

            PaymentResponse response = paymentService.getByTransactionId("TXN-003");
            assertThat(response.getTransactionId()).isEqualTo("TXN-003");
            assertThat(response.getPaymentMethod()).isEqualTo(PaymentMethod.KAKAO_PAY);
        }

        @Test
        @DisplayName("주문번호로 조회 성공")
        void getByOrderNumber_success() {
            Payment payment = Payment.builder()
                    .transactionId("TXN-004")
                    .orderNumber("ORD-004")
                    .amount(new BigDecimal("15000"))
                    .paymentMethod(PaymentMethod.NAVER_PAY)
                    .status(PaymentStatus.COMPLETED)
                    .build();

            given(paymentRepository.findByOrderNumber("ORD-004")).willReturn(Optional.of(payment));

            PaymentResponse response = paymentService.getByOrderNumber("ORD-004");
            assertThat(response.getOrderNumber()).isEqualTo("ORD-004");
        }

        @Test
        @DisplayName("존재하지 않는 주문번호 조회 시 예외")
        void getByOrderNumber_notFound() {
            given(paymentRepository.findByOrderNumber("INVALID")).willReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.getByOrderNumber("INVALID"))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }
}
