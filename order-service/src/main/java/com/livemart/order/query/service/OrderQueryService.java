package com.livemart.order.query.service;

import com.livemart.order.domain.Order;
import com.livemart.order.domain.OrderStatus;
import com.livemart.order.dto.OrderResponse;
import com.livemart.order.dto.OrderItemResponse;
import com.livemart.order.query.dto.OrderStatisticsResponse;
import com.livemart.order.query.dto.OrderSummaryResponse;
import com.livemart.order.repository.OrderRepository;
import com.livemart.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * CQRS Query Side - 읽기 전용 서비스
 * Command와 분리된 조회 로직으로 읽기 성능 최적화
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderQueryService {

    private final OrderRepository orderRepository;

    /**
     * 주문 상세 조회 (캐시 적용)
     */
    @Cacheable(value = "order-detail", key = "#orderId")
    public OrderResponse getOrderDetail(Long orderId) {
        Order order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> BusinessException.notFound("Order", orderId));
        return toDetailResponse(order);
    }

    /**
     * 주문번호로 상세 조회
     */
    public OrderResponse getOrderByNumber(String orderNumber) {
        Order order = orderRepository.findByOrderNumberWithItems(orderNumber)
                .orElseThrow(() -> BusinessException.notFound("Order", orderNumber));
        return toDetailResponse(order);
    }

    /**
     * 사용자별 주문 요약 목록 (경량 DTO)
     */
    public Page<OrderSummaryResponse> getUserOrderSummaries(Long userId, Pageable pageable) {
        return orderRepository.findByUserId(userId, pageable)
                .map(this::toSummaryResponse);
    }

    /**
     * 상태별 주문 요약 목록
     */
    public Page<OrderSummaryResponse> getOrdersByStatus(OrderStatus status, Pageable pageable) {
        return orderRepository.findByStatus(status, pageable)
                .map(this::toSummaryResponse);
    }

    /**
     * 주문 통계 (관리자용)
     *
     * 주의: findAll() 대신 countByStatus()를 사용해 불필요한 엔티티 로딩 방지.
     * 전체 주문을 메모리에 올리면 데이터 증가 시 OOM 위험.
     */
    @Cacheable(value = "order-statistics", key = "'global'")
    public OrderStatisticsResponse getOrderStatistics() {
        long pendingOrders   = orderRepository.countByStatus(OrderStatus.PENDING);
        long confirmedOrders = orderRepository.countByStatus(OrderStatus.CONFIRMED);
        long shippedOrders   = orderRepository.countByStatus(OrderStatus.SHIPPED);
        long deliveredOrders = orderRepository.countByStatus(OrderStatus.DELIVERED);
        long cancelledOrders = orderRepository.countByStatus(OrderStatus.CANCELLED);
        long totalOrders     = pendingOrders + confirmedOrders + shippedOrders + deliveredOrders + cancelledOrders;

        // DB SUM 집계 쿼리로 엔티티 로딩 없이 매출 합산 (OOM 방지)
        BigDecimal totalRevenue = orderRepository.sumTotalAmountExcludingStatus(OrderStatus.CANCELLED);

        long nonCancelledCount = totalOrders - cancelledOrders;
        BigDecimal averageAmount = nonCancelledCount > 0
                ? totalRevenue.divide(BigDecimal.valueOf(nonCancelledCount), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return OrderStatisticsResponse.builder()
                .totalOrders(totalOrders)
                .pendingOrders(pendingOrders)
                .confirmedOrders(confirmedOrders)
                .shippedOrders(shippedOrders)
                .deliveredOrders(deliveredOrders)
                .cancelledOrders(cancelledOrders)
                .totalRevenue(totalRevenue)
                .averageOrderAmount(averageAmount)
                .build();
    }

    private OrderSummaryResponse toSummaryResponse(Order order) {
        return OrderSummaryResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .userId(order.getUserId())
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus())
                .itemCount(order.getItems() != null ? order.getItems().size() : 0)
                .paymentMethod(order.getPaymentMethod())
                .createdAt(order.getCreatedAt())
                .build();
    }

    private OrderResponse toDetailResponse(Order order) {
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
}
