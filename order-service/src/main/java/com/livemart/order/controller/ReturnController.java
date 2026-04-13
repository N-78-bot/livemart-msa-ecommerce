package com.livemart.order.controller;

import com.livemart.order.domain.ReturnRequest;
import com.livemart.order.dto.ReturnRequestDto;
import com.livemart.order.service.ReturnService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Tag(name = "Return API", description = "반품/환불 관리 API")
@RestController
@RequestMapping("/api/returns")
@RequiredArgsConstructor
public class ReturnController {

    private final ReturnService returnService;

    @Operation(summary = "반품/환불 요청")
    @PostMapping
    public ResponseEntity<ReturnRequestDto.Response> createReturn(
            @Valid @RequestBody ReturnRequestDto.Create request) {
        return ResponseEntity.ok(returnService.createReturn(request));
    }

    @Operation(summary = "반품 요청 조회")
    @GetMapping("/{returnId}")
    public ResponseEntity<ReturnRequestDto.Response> getReturn(@PathVariable Long returnId) {
        return ResponseEntity.ok(returnService.getReturn(returnId));
    }

    @Operation(summary = "반품번호로 조회")
    @GetMapping("/number/{returnNumber}")
    public ResponseEntity<ReturnRequestDto.Response> getReturnByNumber(@PathVariable String returnNumber) {
        return ResponseEntity.ok(returnService.getReturnByNumber(returnNumber));
    }

    @Operation(summary = "사용자 반품 내역")
    @GetMapping("/user/{userId}")
    public ResponseEntity<Page<ReturnRequestDto.Response>> getUserReturns(
            @PathVariable Long userId, Pageable pageable, Authentication auth) {
        if (auth != null) {
            Long currentUserId = auth.getPrincipal() instanceof Long l ? l : Long.parseLong(auth.getName());
            boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            if (!isAdmin && !userId.equals(currentUserId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "접근 권한이 없습니다");
            }
        }
        return ResponseEntity.ok(returnService.getUserReturns(userId, pageable));
    }

    @Operation(summary = "상태별 반품 목록 (관리자)")
    @GetMapping("/status/{status}")
    public ResponseEntity<Page<ReturnRequestDto.Response>> getReturnsByStatus(
            @PathVariable ReturnRequest.ReturnStatus status, Pageable pageable) {
        return ResponseEntity.ok(returnService.getReturnsByStatus(status, pageable));
    }

    @Operation(summary = "반품 승인 (관리자)")
    @PostMapping("/{returnId}/approve")
    public ResponseEntity<ReturnRequestDto.Response> approveReturn(
            @PathVariable Long returnId,
            @RequestBody ReturnRequestDto.Approve request) {
        return ResponseEntity.ok(returnService.approveReturn(returnId, request));
    }

    @Operation(summary = "반품 거절 (관리자)")
    @PostMapping("/{returnId}/reject")
    public ResponseEntity<ReturnRequestDto.Response> rejectReturn(
            @PathVariable Long returnId,
            @RequestBody ReturnRequestDto.Reject request) {
        return ResponseEntity.ok(returnService.rejectReturn(returnId, request));
    }

    @Operation(summary = "반품 상태 변경")
    @PutMapping("/{returnId}/status")
    public ResponseEntity<ReturnRequestDto.Response> updateStatus(
            @PathVariable Long returnId,
            @RequestParam ReturnRequest.ReturnStatus status) {
        return ResponseEntity.ok(returnService.updateReturnStatus(returnId, status));
    }

    @Operation(summary = "반품 완료 (환불 처리)")
    @PostMapping("/{returnId}/complete")
    public ResponseEntity<ReturnRequestDto.Response> completeReturn(@PathVariable Long returnId) {
        return ResponseEntity.ok(returnService.completeReturn(returnId));
    }
}
