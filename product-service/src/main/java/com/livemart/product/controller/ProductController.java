package com.livemart.product.controller;

import com.livemart.product.dto.ProductCreateRequest;
import com.livemart.product.dto.ProductResponse;
import com.livemart.product.dto.ProductUpdateRequest;
import com.livemart.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Product API", description = "상품 관리 API")
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @Operation(summary = "상품 생성", description = "새로운 상품을 등록합니다")
    @PostMapping
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody ProductCreateRequest request) {
        ProductResponse response = productService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "상품 수정", description = "상품 정보를 수정합니다")
    @PutMapping("/{productId}")
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable Long productId,
            @Valid @RequestBody ProductUpdateRequest request) {
        ProductResponse response = productService.updateProduct(productId, request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "상품 조회", description = "상품 ID로 상품을 조회합니다")
    @GetMapping("/{productId}")
    public ResponseEntity<ProductResponse> getProduct(@PathVariable Long productId) {
        ProductResponse response = productService.getProduct(productId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "상품 목록 조회", description = "모든 상품 목록을 페이징하여 조회합니다")
    @GetMapping
    public ResponseEntity<Page<ProductResponse>> getProducts(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<ProductResponse> response = productService.getProducts(pageable);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "카테고리별 상품 조회", description = "특정 카테고리의 상품 목록을 조회합니다")
    @GetMapping("/category/{categoryId}")
    public ResponseEntity<Page<ProductResponse>> getProductsByCategory(
            @PathVariable Long categoryId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<ProductResponse> response = productService.getProductsByCategory(categoryId, pageable);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "판매자별 상품 조회", description = "특정 판매자의 상품 목록을 조회합니다")
    @GetMapping("/seller/{sellerId}")
    public ResponseEntity<Page<ProductResponse>> getProductsBySeller(
            @PathVariable Long sellerId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<ProductResponse> response = productService.getProductsBySeller(sellerId, pageable);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "상품 검색", description = "키워드로 상품을 검색합니다")
    @GetMapping("/search")
    public ResponseEntity<Page<ProductResponse>> searchProducts(
            @RequestParam String keyword,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<ProductResponse> response = productService.searchProducts(keyword, pageable);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "재고 수정", description = "상품의 재고 수량을 수정합니다")
    @PatchMapping("/{productId}/stock")
    public ResponseEntity<Void> updateStock(
            @PathVariable Long productId,
            @RequestBody Map<String, Object> request) {
        Integer quantity = ((Number) request.get("stockQuantity")).intValue();
        productService.updateStock(productId, quantity);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "재고 복구", description = "주문 취소 시 재고를 복구합니다")
    @PatchMapping("/{productId}/stock/restore")
    public ResponseEntity<Void> restoreStock(
            @PathVariable Long productId,
            @RequestBody Map<String, Object> request) {
        Integer quantity = ((Number) request.get("stockQuantity")).intValue();
        productService.restoreStock(productId, quantity);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "상품 삭제", description = "상품을 삭제합니다")
    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long productId) {
        productService.deleteProduct(productId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "헬스체크", description = "서비스 상태를 확인합니다")
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Product Service is running");
    }
}