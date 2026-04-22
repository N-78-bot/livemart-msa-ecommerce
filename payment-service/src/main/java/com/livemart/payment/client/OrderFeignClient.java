package com.livemart.payment.client;

import com.livemart.payment.dto.OrderInfo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * order-service 호출 클라이언트 — 결제 금액 검증에 사용
 * 결제 전 서버 측에서 실제 주문 금액과 요청 금액을 비교하여 클라이언트 조작 방어
 */
@FeignClient(name = "order-service", url = "${service.order-url:http://order-service:8087}", path = "/api/orders")
public interface OrderFeignClient {

    @GetMapping("/number/{orderNumber}")
    OrderInfo getOrderByNumber(@PathVariable("orderNumber") String orderNumber);
}
