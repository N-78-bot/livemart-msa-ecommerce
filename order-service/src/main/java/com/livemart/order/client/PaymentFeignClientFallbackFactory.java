package com.livemart.order.client;

import com.livemart.common.exception.BusinessException;
import com.livemart.order.dto.PaymentRequest;
import com.livemart.order.dto.PaymentResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class PaymentFeignClientFallbackFactory implements FallbackFactory<PaymentFeignClient> {

    @Override
    public PaymentFeignClient create(Throwable cause) {
        return new PaymentFeignClient() {
            @Override
            public PaymentResponse processPayment(PaymentRequest request) {
                log.error("Payment Service 호출 실패: order={}", request.getOrderNumber(), cause);
                throw BusinessException.paymentFailed("결제 처리에 실패했습니다.");
            }

            @Override
            public void cancelPayment(String transactionId, Map<String, String> body) {
                log.error("Payment Service 취소 실패: transactionId={}", transactionId, cause);
                throw BusinessException.paymentFailed("결제 취소에 실패했습니다.");
            }
        };
    }
}
