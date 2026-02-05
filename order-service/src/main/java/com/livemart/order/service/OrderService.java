package com.livemart.order.service;

import com.livemart.order.aspect.DistributedLock;
import com.livemart.order.client.PaymentServiceClient;
import com.livemart.order.client.ProductServiceClient;
import com.livemart.order.domain.Order;
import com.livemart.order.domain.OrderItem;
import com.livemart.order.domain.OrderStatus;
import com.livemart.order.dto.*;
import com.livemart.order.event.OrderEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.livemart.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {
    private final OrderRepository orderRepository;
    private final ProductServiceClient productServiceClient;
    private final PaymentServiceClient paymentServiceClient;
    private final KafkaTemplate<String, OrderEvent> kafkaTemplate;

    @DistributedLock(key = "#request.items[0].productId", waitTime = 10, leaseTime = 5)
    public OrderResponse createOrder(OrderCreateRequest request) {
        return createOrderInternal(request);
    }

    @Transactional
    private OrderResponse createOrderInternal(OrderCreateRequest request) {
        Long userId = request.getUserId();
        log.info("Creating order for userId: {}", userId);

        // 1. 상품 정보 조회 및 검증
        List<OrderItem> orderItems = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (OrderItemRequest itemRequest : request.getItems()) {
            ProductInfo product = productServiceClient.getProduct(itemRequest.getProductId());

            if (product.getStockQuantity() < itemRequest.getQuantity()) {
                throw new RuntimeException("재고가 부족합니다: " + product.getName());
            }

            BigDecimal itemTotal = product.getPrice().multiply(new BigDecimal(itemRequest.getQuantity()));
            totalAmount = totalAmount.add(itemTotal);

            OrderItem orderItem = OrderItem.builder()
                    .productId(product.getId())
                    .productName(product.getName())
                    .productPrice(product.getPrice())
                    .quantity(itemRequest.getQuantity())
                    .totalPrice(itemTotal)
                    .build();

            orderItems.add(orderItem);
        }

        // 2. 주문 생성
        String orderNumber = generateOrderNumber();
        Order order = Order.builder()
                .orderNumber(orderNumber)
                .userId(userId)
                .totalAmount(totalAmount)
                .status(OrderStatus.PENDING)
                .deliveryAddress(request.getDeliveryAddress())
                .phoneNumber(request.getPhoneNumber())
                .orderNote(request.getOrderNote())
                .paymentMethod(request.getPaymentMethod())
                .build();

        for (OrderItem item : orderItems) {
            order.addOrderItem(item);
        }

        orderRepository.save(order);

        // 3. 재고 차감
        try {
            for (OrderItemRequest itemRequest : request.getItems()) {
                ProductInfo product = productServiceClient.getProduct(itemRequest.getProductId());
                int newStock = product.getStockQuantity() - itemRequest.getQuantity();
                productServiceClient.updateStock(itemRequest.getProductId(), newStock);
            }
        } catch (Exception e) {
            log.error("Failed to update stock. Rolling back order: {}", orderNumber, e);
            orderRepository.delete(order);
            throw new RuntimeException("재고 업데이트 실패. 주문이 취소되었습니다.");
        }

        // 4. 결제 처리
        PaymentRequest paymentRequest = PaymentRequest.builder()
                .orderNumber(order.getOrderNumber())
                .userId(userId)
                .amount(totalAmount)
                .method(request.getPaymentMethod())
                .cardNumber("1234567812345678")
                .build();

        try {
            PaymentResponse paymentResponse = paymentServiceClient.processPayment(paymentRequest);
            order.setPaymentTransactionId(paymentResponse.getTransactionId());
            log.info("결제 완료: transactionId={}", paymentResponse.getTransactionId());
        } catch (Exception e) {
            log.error("결제 실패. 주문 롤백: {}", order.getOrderNumber(), e);
            orderRepository.delete(order);
            throw new RuntimeException("결제 처리 실패");
        }

        // 5. 주문 이벤트 발행
        publishOrderEvent(order, "ORDER_CREATED");

        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("주문을 찾을 수 없습니다."));
        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getUserOrders(Long userId) {
        return orderRepository.findByUserId(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    private String generateOrderNumber() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String random = String.format("%08X", new Random().nextInt());
        return "ORD-" + timestamp + "-" + random;
    }

    private void publishOrderEvent(Order order, String eventType) {
        List<OrderEvent.OrderItemInfo> items = order.getItems().stream()
                .map(item -> OrderEvent.OrderItemInfo.builder()
                        .productId(item.getProductId())
                        .productName(item.getProductName())
                        .quantity(item.getQuantity())
                        .price(item.getProductPrice())
                        .build())
                .toList();

        OrderEvent event = OrderEvent.builder()
                .eventType(OrderEvent.EventType.valueOf(eventType))
                .orderNumber(order.getOrderNumber())
                .userId(order.getUserId())
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus())
                .items(items)
                .occurredAt(LocalDateTime.now())
                .build();

        kafkaTemplate.send("order-events", order.getOrderNumber(), event);
        log.info("Order event published: {}", eventType);
    }

    private OrderResponse toResponse(Order order) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(item -> OrderItemResponse.builder()
                        .id(item.getId())
                        .productId(item.getProductId())
                        .productName(item.getProductName())
                        .productPrice(item.getProductPrice())
                        .quantity(item.getQuantity())
                        .totalPrice(item.getTotalPrice())
                        .build())
                .toList();

        return OrderResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .userId(order.getUserId())
                .items(items)
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus())
                .deliveryAddress(order.getDeliveryAddress())
                .phoneNumber(order.getPhoneNumber())
                .orderNote(order.getOrderNote())
                .paymentMethod(order.getPaymentMethod())
                .paymentTransactionId(order.getPaymentTransactionId())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .confirmedAt(order.getConfirmedAt())
                .shippedAt(order.getShippedAt())
                .deliveredAt(order.getDeliveredAt())
                .cancelledAt(order.getCancelledAt())
                .build();
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderByOrderNumber(String orderNumber) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new RuntimeException("주문을 찾을 수 없습니다."));
        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> getOrdersByUserId(Long userId, Pageable pageable) {
        return orderRepository.findByUserId(userId, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> getOrdersByStatus(OrderStatus status, Pageable pageable) {
        return orderRepository.findByStatus(status, pageable)
                .map(this::toResponse);
    }

    @Transactional
    public OrderResponse confirmOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("주문을 찾을 수 없습니다."));
        order.confirm();
        publishOrderEvent(order, "ORDER_CONFIRMED");
        return toResponse(order);
    }

    @Transactional
    public OrderResponse shipOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("주문을 찾을 수 없습니다."));
        order.ship();
        publishOrderEvent(order, "ORDER_SHIPPED");
        return toResponse(order);
    }

    @Transactional
    public OrderResponse deliverOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("주문을 찾을 수 없습니다."));
        order.deliver();
        publishOrderEvent(order, "ORDER_DELIVERED");
        return toResponse(order);
    }

    @Transactional
    public OrderResponse cancelOrder(Long orderId, String reason) {
        log.info("Cancelling order: {}", orderId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("주문을 찾을 수 없습니다."));

        if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.CONFIRMED) {
            throw new RuntimeException("취소할 수 없는 주문 상태입니다.");
        }

        try {
            if (order.getPaymentTransactionId() != null) {
                paymentServiceClient.cancelPayment(order.getPaymentTransactionId(), reason);
                log.info("Payment cancelled: {}", order.getPaymentTransactionId());
            }

            for (OrderItem item : order.getItems()) {
                productServiceClient.restoreStock(item.getProductId(), item.getQuantity());
                log.info("Stock restored: productId={}, quantity={}", item.getProductId(), item.getQuantity());
            }

            order.cancel();
            publishOrderEvent(order, "ORDER_CANCELLED");

            return toResponse(order);

        } catch (Exception e) {
            log.error("Failed to cancel order: {}", orderId, e);
            throw new RuntimeException("주문 취소에 실패했습니다: " + e.getMessage());
        }
    }
}