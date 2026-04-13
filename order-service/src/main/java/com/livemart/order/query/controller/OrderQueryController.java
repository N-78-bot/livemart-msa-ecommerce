package com.livemart.order.query.controller;

import com.livemart.order.domain.OrderStatus;
import com.livemart.order.dto.OrderResponse;
import com.livemart.order.query.dto.OrderStatisticsResponse;
import com.livemart.order.query.dto.OrderSummaryResponse;
import com.livemart.order.query.service.OrderQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * CQRS Query Controller - 읽기 전용 API
 */
@Tag(name = "Order Query API", description = "주문 조회 API (CQRS Query Side)")
@RestController
@RequestMapping("/api/orders/query")
@RequiredArgsConstructor
public class OrderQueryController {

    private final OrderQueryService orderQueryService;

    @Operation(summary = "주문 상세 조회", description = "주문 ID로 상세 정보를 조회합니다 (캐시 적용)")
    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrderDetail(@PathVariable Long orderId) {
        return ResponseEntity.ok(orderQueryService.getOrderDetail(orderId));
    }

    @Operation(summary = "주문번호 조회", description = "주문번호로 상세 정보를 조회합니다")
    @GetMapping("/number/{orderNumber}")
    public ResponseEntity<OrderResponse> getOrderByNumber(@PathVariable String orderNumber) {
        return ResponseEntity.ok(orderQueryService.getOrderByNumber(orderNumber));
    }

    @Operation(summary = "사용자 주문 요약 목록", description = "사용자의 주문 요약 목록 (경량 DTO)")
    @GetMapping("/user/{userId}/summary")
    public ResponseEntity<Page<OrderSummaryResponse>> getUserOrderSummaries(
            @PathVariable Long userId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication auth) {
        if (auth != null) {
            Long currentUserId = auth.getPrincipal() instanceof Long l ? l : Long.parseLong(auth.getName());
            boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            if (!isAdmin && !userId.equals(currentUserId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "접근 권한이 없습니다");
            }
        }
        return ResponseEntity.ok(orderQueryService.getUserOrderSummaries(userId, pageable));
    }

    @Operation(summary = "상태별 주문 조회", description = "주문 상태별로 요약 목록을 조회합니다")
    @GetMapping("/status/{status}")
    public ResponseEntity<Page<OrderSummaryResponse>> getOrdersByStatus(
            @PathVariable OrderStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(orderQueryService.getOrdersByStatus(status, pageable));
    }

    @Operation(summary = "주문 통계", description = "전체 주문 통계를 조회합니다 (관리자용)")
    @GetMapping("/statistics")
    public ResponseEntity<OrderStatisticsResponse> getOrderStatistics() {
        return ResponseEntity.ok(orderQueryService.getOrderStatistics());
    }
}
