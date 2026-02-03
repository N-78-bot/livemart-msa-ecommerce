package com.livemart.product.controller;

import com.livemart.product.dto.CategoryResponse;
import com.livemart.product.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Category API", description = "카테고리 관리 API")
@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @Operation(summary = "최상위 카테고리 조회", description = "모든 최상위 카테고리를 조회합니다")
    @GetMapping("/root")
    public ResponseEntity<List<CategoryResponse>> getRootCategories() {
        List<CategoryResponse> response = categoryService.getRootCategories();
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "하위 카테고리 조회", description = "특정 카테고리의 하위 카테고리를 조회합니다")
    @GetMapping("/{categoryId}/sub")
    public ResponseEntity<List<CategoryResponse>> getSubCategories(@PathVariable Long categoryId) {
        List<CategoryResponse> response = categoryService.getSubCategories(categoryId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "카테고리 조회", description = "카테고리 ID로 카테고리를 조회합니다")
    @GetMapping("/{categoryId}")
    public ResponseEntity<CategoryResponse> getCategory(@PathVariable Long categoryId) {
        CategoryResponse response = categoryService.getCategory(categoryId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "카테고리 생성", description = "새로운 카테고리를 생성합니다")
    @PostMapping
    public ResponseEntity<CategoryResponse> createCategory(
            @RequestParam String name,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) Long parentId) {
        CategoryResponse response = categoryService.createCategory(name, description, parentId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}