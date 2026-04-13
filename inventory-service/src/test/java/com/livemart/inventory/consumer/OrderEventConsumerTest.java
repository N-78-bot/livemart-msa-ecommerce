package com.livemart.inventory.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.livemart.inventory.dto.InventoryRequest;
import com.livemart.inventory.service.InventoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderEventConsumer 단위 테스트")
class OrderEventConsumerTest {

    @Mock  InventoryService inventoryService;
    @Spy   ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks OrderEventConsumer consumer;

    @Test
    @DisplayName("ORDER_CREATED: 아이템별 재고 예약 호출")
    void handleOrderCreated_reservesStock() throws Exception {
        String message = """
            {
              "eventType": "ORDER_CREATED",
              "payload": {
                "orderId": "ORD-001",
                "items": [
                  {"productId": 1, "quantity": 2},
                  {"productId": 2, "quantity": 1}
                ]
              }
            }
            """;

        consumer.handleOrderEvent(message);

        ArgumentCaptor<InventoryRequest.Reserve> captor = ArgumentCaptor.forClass(InventoryRequest.Reserve.class);
        then(inventoryService).should(times(2)).reserveStock(captor.capture());

        var reservations = captor.getAllValues();
        assertThat(reservations).extracting(InventoryRequest.Reserve::getProductId)
                .containsExactlyInAnyOrder(1L, 2L);
        assertThat(reservations).extracting(InventoryRequest.Reserve::getQuantity)
                .containsExactlyInAnyOrder(2, 1);
    }

    @Test
    @DisplayName("ORDER_CANCELLED: 아이템별 재고 예약 취소 호출")
    void handleOrderCancelled_cancelsReservation() throws Exception {
        String message = """
            {
              "eventType": "ORDER_CANCELLED",
              "payload": {
                "orderId": "ORD-002",
                "items": [
                  {"productId": 10, "quantity": 3}
                ]
              }
            }
            """;

        consumer.handleOrderEvent(message);

        then(inventoryService).should().cancelReservation(10L, 3, "ORD-002");
    }

    @Test
    @DisplayName("알 수 없는 이벤트 타입: InventoryService 미호출")
    void handleUnknownEvent_ignores() throws Exception {
        String message = """
            {"eventType": "UNKNOWN_EVENT", "payload": {}}
            """;

        consumer.handleOrderEvent(message);

        then(inventoryService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("InventoryService 예외 발생: 예외 전파 (DLQ 위임)")
    void handleOrderCreated_serviceThrows_propagatesException() {
        String message = """
            {
              "eventType": "ORDER_CREATED",
              "payload": {
                "orderId": "ORD-003",
                "items": [{"productId": 5, "quantity": 1}]
              }
            }
            """;

        willThrow(new IllegalStateException("재고 부족"))
                .given(inventoryService).reserveStock(any());

        // 예외가 전파되어 Kafka DLQ 핸들러로 위임됨
        assertThatThrownBy(() -> consumer.handleOrderEvent(message))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("재고 부족");
    }

    @Test
    @DisplayName("잘못된 JSON: 예외 전파 (파싱 실패)")
    void handleMalformedJson_propagatesException() {
        assertThatThrownBy(() -> consumer.handleOrderEvent("not-json"))
                .isInstanceOf(Exception.class);
    }
}
