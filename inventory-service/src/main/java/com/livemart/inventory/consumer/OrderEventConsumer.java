package com.livemart.inventory.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.livemart.inventory.dto.InventoryRequest;
import com.livemart.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 주문 이벤트 컨슈머
 *
 * 예외 처리 전략: try-catch 없이 예외를 전파 → KafkaConfig.inventoryKafkaErrorHandler 위임
 * - 처리 실패 시 지수 백오프(1s→2s→4s, 3회) 재시도
 * - 재시도 소진 시 order-events.DLT 이동 (재고 유실 방지)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventConsumer {

    private final InventoryService inventoryService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "order-events", groupId = "inventory-service")
    public void handleOrderEvent(String message) throws Exception {
        JsonNode event = objectMapper.readTree(message);
        String eventType = event.path("eventType").asText();
        JsonNode payload = event.has("payload") && event.get("payload").isTextual()
                ? objectMapper.readTree(event.get("payload").asText())
                : event.path("payload");

        log.debug("재고 이벤트 수신: type={}", eventType);

        switch (eventType) {
            case "ORDER_CREATED"   -> handleOrderCreated(payload);
            case "ORDER_CANCELLED" -> handleOrderCancelled(payload);
            default -> log.debug("무시된 이벤트: {}", eventType);
        }
    }

    private void handleOrderCreated(JsonNode payload) {
        JsonNode items = payload.path("items");
        if (!items.isArray()) return;
        String orderId = payload.path("orderId").asText();
        for (JsonNode item : items) {
            long productId = item.path("productId").asLong();
            int quantity   = item.path("quantity").asInt();
            inventoryService.reserveStock(InventoryRequest.Reserve.builder()
                    .productId(productId).quantity(quantity).orderId(orderId).build());
        }
    }

    private void handleOrderCancelled(JsonNode payload) {
        JsonNode items = payload.path("items");
        if (!items.isArray()) return;
        String orderId = payload.path("orderId").asText();
        for (JsonNode item : items) {
            long productId = item.path("productId").asLong();
            int quantity   = item.path("quantity").asInt();
            inventoryService.cancelReservation(productId, quantity, orderId);
        }
    }
}
