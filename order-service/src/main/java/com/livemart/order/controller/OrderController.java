package com.livemart.order.controller;

import com.livemart.order.domain.OrderStatus;
import com.livemart.order.dto.OrderCreateRequest;
import com.livemart.order.dto.OrderResponse;
import com.livemart.order.service.OrderService;
import com.livemart.common.idempotency.IdempotencyKey;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Tag(name = "Order API", description = "주문 관리 API")
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @Operation(summary = "전체 주문 목록 조회 (관리자용)")
    @GetMapping
    public ResponseEntity<Page<OrderResponse>> getAllOrders(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication auth) {
        // 관리자는 전체 목록, 일반 사용자는 본인 주문만 반환
        if (hasAdminRole(auth)) {
            return ResponseEntity.ok(orderService.getAllOrders(pageable));
        }
        Long userId = extractUserId(auth);
        return ResponseEntity.ok(orderService.getOrdersByUserId(userId, pageable));
    }

    @Operation(
        summary = "주문 생성",
        description = "새로운 주문을 생성합니다 (Saga Pattern). Idempotency-Key 헤더로 중복 요청을 방지할 수 있습니다."
    )
    @IdempotencyKey(prefix = "order-create", ttlSeconds = 86400)
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @Parameter(description = "멱등성 키 (UUID 권장, 24시간 유효)", example = "550e8400-e29b-41d4-a716-446655440000")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyHeader,
            @Valid @RequestBody OrderCreateRequest request) {
        OrderResponse response = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "주문 확인", description = "주문을 확인 상태로 변경합니다 (결제 완료 후)")
    @PostMapping("/{orderId}/confirm")
    public ResponseEntity<OrderResponse> confirmOrder(@PathVariable Long orderId) {
        OrderResponse response = orderService.confirmOrder(orderId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "배송 시작", description = "주문의 배송을 시작합니다")
    @PostMapping("/{orderId}/ship")
    public ResponseEntity<OrderResponse> shipOrder(@PathVariable Long orderId) {
        OrderResponse response = orderService.shipOrder(orderId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "배송 완료", description = "주문의 배송을 완료합니다")
    @PostMapping("/{orderId}/deliver")
    public ResponseEntity<OrderResponse> deliverOrder(@PathVariable Long orderId) {
        OrderResponse response = orderService.deliverOrder(orderId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "주문 취소", description = "주문을 취소합니다 (재고 복구)")
    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<OrderResponse> cancelOrder(
            @PathVariable Long orderId,
            @RequestParam String reason) {
        OrderResponse response = orderService.cancelOrder(orderId, reason);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "주문 조회", description = "주문 ID로 주문을 조회합니다")
    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable Long orderId, Authentication auth) {
        OrderResponse response = orderService.getOrder(orderId);
        Long currentUserId = extractUserId(auth);
        if (!response.getUserId().equals(currentUserId) && !hasAdminRole(auth)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "접근 권한이 없습니다");
        }
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "주문 번호로 조회", description = "주문 번호로 주문을 조회합니다")
    @GetMapping("/number/{orderNumber}")
    public ResponseEntity<OrderResponse> getOrderByNumber(@PathVariable String orderNumber, Authentication auth) {
        OrderResponse response = orderService.getOrderByOrderNumber(orderNumber);
        Long currentUserId = extractUserId(auth);
        if (!response.getUserId().equals(currentUserId) && !hasAdminRole(auth)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "접근 권한이 없습니다");
        }
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "사용자별 주문 목록", description = "특정 사용자의 주문 목록을 조회합니다")
    @GetMapping("/user/{userId}")
    public ResponseEntity<Page<OrderResponse>> getOrdersByUserId(
            @PathVariable Long userId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication auth) {
        Long currentUserId = extractUserId(auth);
        if (!userId.equals(currentUserId) && !hasAdminRole(auth)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "접근 권한이 없습니다");
        }
        Page<OrderResponse> response = orderService.getOrdersByUserId(userId, pageable);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "상태별 주문 목록", description = "주문 상태별로 주문 목록을 조회합니다")
    @GetMapping("/status/{status}")
    public ResponseEntity<Page<OrderResponse>> getOrdersByStatus(
            @PathVariable OrderStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<OrderResponse> response = orderService.getOrdersByStatus(status, pageable);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "헬스체크", description = "서비스 상태를 확인합니다")
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Order Service is running");
    }

    // ── 보안 헬퍼 메서드 ─────────────────────────────────────────────

    private Long extractUserId(Authentication auth) {
        if (auth == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "인증이 필요합니다");
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof Long) return (Long) principal;
        if (principal instanceof String) return Long.parseLong((String) principal);
        return Long.parseLong(auth.getName());
    }

    private boolean hasAdminRole(Authentication auth) {
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}