package com.livemart.order.service;

import com.livemart.order.domain.Order;
import com.livemart.order.domain.OrderStatus;
import com.livemart.order.domain.ReturnRequest;
import com.livemart.order.dto.ReturnRequestDto;
import com.livemart.order.repository.OrderRepository;
import com.livemart.order.repository.ReturnRequestRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReturnService 단위 테스트")
class ReturnServiceTest {

    @InjectMocks
    private ReturnService returnService;

    @Mock
    private ReturnRequestRepository returnRequestRepository;

    @Mock
    private OrderRepository orderRepository;

    private Order createTestOrder(OrderStatus status) {
        return Order.builder()
                .id(1L)
                .orderNumber("ORD-001")
                .userId(10L)
                .totalAmount(new BigDecimal("50000"))
                .status(status)
                .deliveryAddress("서울시 강남구")
                .phoneNumber("010-1234-5678")
                .paymentMethod("CARD")
                .build();
    }

    @Nested
    @DisplayName("반품 요청 생성")
    class CreateReturnTest {

        @Test
        @DisplayName("배송 완료 주문 반품 요청 성공")
        void createReturn_delivered() {
            Order order = createTestOrder(OrderStatus.DELIVERED);
            given(orderRepository.findById(1L)).willReturn(Optional.of(order));
            given(returnRequestRepository.save(any(ReturnRequest.class))).willAnswer(inv -> inv.getArgument(0));

            ReturnRequestDto.Create request = ReturnRequestDto.Create.builder()
                    .orderId(1L)
                    .userId(10L)
                    .returnType(ReturnRequest.ReturnType.RETURN)
                    .reason(ReturnRequest.ReturnReason.DEFECTIVE)
                    .reasonDetail("상품에 흠집이 있습니다")
                    .build();

            ReturnRequestDto.Response response = returnService.createReturn(request);

            assertThat(response.getReturnNumber()).startsWith("RTN-");
            assertThat(response.getStatus()).isEqualTo(ReturnRequest.ReturnStatus.REQUESTED);
            assertThat(response.getReason()).isEqualTo(ReturnRequest.ReturnReason.DEFECTIVE);
        }

        @Test
        @DisplayName("배송 전 주문 반품 요청 시 예외")
        void createReturn_pendingOrder() {
            Order order = createTestOrder(OrderStatus.PENDING);
            given(orderRepository.findById(1L)).willReturn(Optional.of(order));

            ReturnRequestDto.Create request = ReturnRequestDto.Create.builder()
                    .orderId(1L).userId(10L)
                    .returnType(ReturnRequest.ReturnType.RETURN)
                    .reason(ReturnRequest.ReturnReason.CHANGED_MIND)
                    .build();

            assertThatThrownBy(() -> returnService.createReturn(request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("배송 완료 또는 확인된 주문만");
        }

        @Test
        @DisplayName("타인의 주문 반품 요청 시 예외")
        void createReturn_notOwner() {
            Order order = createTestOrder(OrderStatus.DELIVERED);
            given(orderRepository.findById(1L)).willReturn(Optional.of(order));

            ReturnRequestDto.Create request = ReturnRequestDto.Create.builder()
                    .orderId(1L).userId(99L)  // 다른 사용자
                    .returnType(ReturnRequest.ReturnType.RETURN)
                    .reason(ReturnRequest.ReturnReason.WRONG_ITEM)
                    .build();

            assertThatThrownBy(() -> returnService.createReturn(request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("본인의 주문만");
        }
    }

    @Nested
    @DisplayName("반품 승인/거절")
    class ApproveRejectTest {

        @Test
        @DisplayName("반품 승인 성공")
        void approveReturn_success() {
            Order order = createTestOrder(OrderStatus.DELIVERED);
            ReturnRequest returnRequest = ReturnRequest.builder()
                    .id(1L).returnNumber("RTN-001").order(order).userId(10L)
                    .returnType(ReturnRequest.ReturnType.RETURN)
                    .status(ReturnRequest.ReturnStatus.REQUESTED)
                    .reason(ReturnRequest.ReturnReason.DEFECTIVE)
                    .build();

            given(returnRequestRepository.findById(1L)).willReturn(Optional.of(returnRequest));

            ReturnRequestDto.Approve approve = ReturnRequestDto.Approve.builder()
                    .refundAmount(new BigDecimal("50000"))
                    .adminNote("불량 확인, 전액 환불")
                    .build();

            ReturnRequestDto.Response response = returnService.approveReturn(1L, approve);

            assertThat(response.getStatus()).isEqualTo(ReturnRequest.ReturnStatus.APPROVED);
            assertThat(response.getRefundAmount()).isEqualByComparingTo(new BigDecimal("50000"));
        }

        @Test
        @DisplayName("반품 거절 성공")
        void rejectReturn_success() {
            Order order = createTestOrder(OrderStatus.DELIVERED);
            ReturnRequest returnRequest = ReturnRequest.builder()
                    .id(1L).returnNumber("RTN-002").order(order).userId(10L)
                    .status(ReturnRequest.ReturnStatus.REQUESTED)
                    .returnType(ReturnRequest.ReturnType.RETURN)
                    .reason(ReturnRequest.ReturnReason.CHANGED_MIND)
                    .build();

            given(returnRequestRepository.findById(1L)).willReturn(Optional.of(returnRequest));

            ReturnRequestDto.Reject reject = ReturnRequestDto.Reject.builder()
                    .adminNote("반품 기간 초과")
                    .build();

            ReturnRequestDto.Response response = returnService.rejectReturn(1L, reject);

            assertThat(response.getStatus()).isEqualTo(ReturnRequest.ReturnStatus.REJECTED);
        }

        @Test
        @DisplayName("이미 처리된 반품 승인 시 예외")
        void approveReturn_alreadyProcessed() {
            Order order = createTestOrder(OrderStatus.DELIVERED);
            ReturnRequest returnRequest = ReturnRequest.builder()
                    .id(1L).order(order)
                    .status(ReturnRequest.ReturnStatus.APPROVED)
                    .returnType(ReturnRequest.ReturnType.RETURN)
                    .reason(ReturnRequest.ReturnReason.DEFECTIVE)
                    .build();

            given(returnRequestRepository.findById(1L)).willReturn(Optional.of(returnRequest));

            assertThatThrownBy(() -> returnService.approveReturn(1L, ReturnRequestDto.Approve.builder().build()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("요청 상태의 반품만");
        }
    }

    @Test
    @DisplayName("반품 완료 시 주문 상태도 CANCELLED로 변경")
    void completeReturn_orderCancelled() {
        Order order = createTestOrder(OrderStatus.DELIVERED);
        ReturnRequest returnRequest = ReturnRequest.builder()
                .id(1L).returnNumber("RTN-003").order(order).userId(10L)
                .status(ReturnRequest.ReturnStatus.RECEIVED)
                .returnType(ReturnRequest.ReturnType.RETURN)
                .reason(ReturnRequest.ReturnReason.DEFECTIVE)
                .refundAmount(new BigDecimal("50000"))
                .build();

        given(returnRequestRepository.findById(1L)).willReturn(Optional.of(returnRequest));

        ReturnRequestDto.Response response = returnService.completeReturn(1L);

        assertThat(response.getStatus()).isEqualTo(ReturnRequest.ReturnStatus.COMPLETED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }
}
