package com.livemart.order.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCreateRequest {

    @NotNull(message = "사용자 ID는 필수입니다")
    private Long userId;

    @NotEmpty(message = "주문 항목은 최소 1개 이상이어야 합니다")
    private List<OrderItemRequest> items;

    @NotBlank(message = "배송지 주소는 필수입니다")
    @Size(max = 200, message = "배송지 주소는 200자 이하여야 합니다")
    private String deliveryAddress;

    @NotBlank(message = "연락처는 필수입니다")
    @Pattern(regexp = "^01[0-9]-\\d{3,4}-\\d{4}$", message = "올바른 전화번호 형식이 아닙니다")
    private String phoneNumber;

    @Size(max = 500, message = "주문 메모는 500자 이하여야 합니다")
    private String orderNote;

    @NotBlank(message = "결제 방법은 필수입니다")
    private String paymentMethod;
}