package com.livemart.payment.service;

import com.livemart.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TossPaymentClient 단위 테스트")
class TossPaymentClientTest {

    @Spy
    private TossPaymentClient tossPaymentClient = new TossPaymentClient();

    private RestTemplate mockRestTemplate;

    @BeforeEach
    void setUp() {
        mockRestTemplate = mock(RestTemplate.class);
        ReflectionTestUtils.setField(tossPaymentClient, "secretKey", "test_sk_dummy");
        ReflectionTestUtils.setField(tossPaymentClient, "restTemplate", mockRestTemplate);
    }

    @Test
    @DisplayName("결제 승인 4xx: BusinessException.paymentFailed 반환")
    void confirm_4xx_throwsPaymentFailed() {
        given(mockRestTemplate.exchange(anyString(), any(), any(), any(Class.class)))
                .willThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST, "invalid request"));

        assertThatThrownBy(() -> tossPaymentClient.confirm("pk", "orderId", 10000L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo("PAYMENT_FAILED");
                    assertThat(be.getStatus()).isEqualTo(422);
                });
    }

    @Test
    @DisplayName("결제 승인 5xx: BusinessException.paymentFailed 반환")
    void confirm_5xx_throwsPaymentFailed() {
        given(mockRestTemplate.exchange(anyString(), any(), any(), any(Class.class)))
                .willThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> tossPaymentClient.confirm("pk", "orderId", 10000L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getStatus()).isEqualTo(422));
    }

    @Test
    @DisplayName("결제 승인 네트워크 오류: BusinessException.paymentFailed 반환")
    void confirm_networkError_throwsPaymentFailed() {
        given(mockRestTemplate.exchange(anyString(), any(), any(), any(Class.class)))
                .willThrow(new ResourceAccessException("Connection timed out"));

        assertThatThrownBy(() -> tossPaymentClient.confirm("pk", "orderId", 10000L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getStatus()).isEqualTo(422));
    }

    @Test
    @DisplayName("결제 취소 4xx: BusinessException.paymentFailed 반환")
    void cancel_4xx_throwsPaymentFailed() {
        given(mockRestTemplate.exchange(anyString(), any(), any(), any(Class.class)))
                .willThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND, "payment not found"));

        assertThatThrownBy(() -> tossPaymentClient.cancel("pk", "사용자 취소", null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getStatus()).isEqualTo(422));
    }

    @Test
    @DisplayName("결제 취소 5xx: BusinessException.paymentFailed 반환")
    void cancel_5xx_throwsPaymentFailed() {
        given(mockRestTemplate.exchange(anyString(), any(), any(), any(Class.class)))
                .willThrow(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> tossPaymentClient.cancel("pk", "취소", 5000L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getStatus()).isEqualTo(422));
    }
}
