package com.livemart.product.controller;

import com.livemart.product.dto.ReviewRequest;
import com.livemart.product.dto.ReviewResponse;
import com.livemart.product.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Tag(name = "Review API", description = "상품 리뷰 관리 API")
@RestController
@RequestMapping("/api/products/{productId}/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @Operation(summary = "리뷰 작성")
    @PostMapping
    public ResponseEntity<ReviewResponse> createReview(
            @PathVariable Long productId,
            @Valid @RequestBody ReviewRequest request) {
        return ResponseEntity.ok(reviewService.createReview(productId, request));
    }

    @Operation(summary = "상품 리뷰 목록 조회")
    @GetMapping
    public ResponseEntity<Page<ReviewResponse>> getProductReviews(
            @PathVariable Long productId, Pageable pageable) {
        return ResponseEntity.ok(reviewService.getProductReviews(productId, pageable));
    }

    @Operation(summary = "리뷰 평점 요약")
    @GetMapping("/summary")
    public ResponseEntity<ReviewResponse.ReviewSummary> getReviewSummary(@PathVariable Long productId) {
        return ResponseEntity.ok(reviewService.getReviewSummary(productId));
    }

    @Operation(summary = "리뷰 수정")
    @PutMapping("/{reviewId}")
    public ResponseEntity<ReviewResponse> updateReview(
            @PathVariable Long productId,
            @PathVariable Long reviewId,
            @RequestHeader(value = "X-User-Id", required = false) String xUserId,
            @Valid @RequestBody ReviewRequest request) {
        Long userId = parseUserId(xUserId);
        return ResponseEntity.ok(reviewService.updateReview(reviewId, userId, request));
    }

    @Operation(summary = "리뷰 삭제")
    @DeleteMapping("/{reviewId}")
    public ResponseEntity<Void> deleteReview(
            @PathVariable Long productId,
            @PathVariable Long reviewId,
            @RequestHeader(value = "X-User-Id", required = false) String xUserId) {
        Long userId = parseUserId(xUserId);
        reviewService.deleteReview(reviewId, userId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "리뷰 도움됨 표시")
    @PostMapping("/{reviewId}/helpful")
    public ResponseEntity<Void> markHelpful(
            @PathVariable Long productId,
            @PathVariable Long reviewId) {
        reviewService.markHelpful(reviewId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "사용자 리뷰 조회")
    @GetMapping("/user/{userId}")
    public ResponseEntity<Page<ReviewResponse>> getUserReviews(
            @PathVariable Long productId,
            @PathVariable Long userId,
            Pageable pageable) {
        return ResponseEntity.ok(reviewService.getUserReviews(userId, pageable));
    }

    private Long parseUserId(String xUserId) {
        if (xUserId == null || xUserId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "인증이 필요합니다");
        }
        try {
            return Long.parseLong(xUserId);
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "잘못된 사용자 ID 형식입니다");
        }
    }
}
