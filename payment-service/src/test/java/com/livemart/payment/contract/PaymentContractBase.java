package com.livemart.payment.contract;

import com.livemart.payment.controller.PaymentController;
import com.livemart.payment.dto.PaymentRequest;
import com.livemart.payment.dto.PaymentResponse;
import com.livemart.payment.service.PaymentService;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.BDDMockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.mockito.ArgumentMatchers.any;

/**
 * Spring Cloud Contract — Provider (payment-service) 테스트 베이스 클래스
 *
 * 계약 파일(*.groovy)로부터 자동 생성되는 검증 테스트의 기반 클래스.
 * 여기서 Mock 설정을 하면 계약 테스트가 실제 DB/Kafka 없이 실행된다.
 */
@WebMvcTest(PaymentController.class)
public abstract class PaymentContractBase {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        RestAssuredMockMvc.mockMvc(mockMvc);
        setupMocks();
    }

    private void setupMocks() {
        // POST /api/v1/payments 응답 Mock
        PaymentResponse completedPayment = PaymentResponse.builder()
                .id(1L)
                .orderNumber("ORD-20260101-TEST0001")
                .transactionId("txn_test_stripe_001")
                .status(com.livemart.payment.domain.PaymentStatus.COMPLETED)
                .amount(BigDecimal.valueOf(50000))
                .paymentMethod(com.livemart.payment.domain.PaymentMethod.CREDIT_CARD)
                .createdAt(OffsetDateTime.now())
                .build();

        BDDMockito.given(paymentService.processPayment(any(PaymentRequest.Create.class)))
                .willReturn(completedPayment);

        // GET /api/v1/payments/order/{orderNumber} 응답 Mock
        BDDMockito.given(paymentService.getByOrderNumber("ORD-20260101-TEST0001"))
                .willReturn(completedPayment);
    }
}
