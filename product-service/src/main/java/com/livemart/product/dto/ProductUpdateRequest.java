package com.livemart.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ProductUpdateRequest {

    @Size(max = 200, message = "상품명은 200자 이하여야 합니다")
    private String name;

    @Size(max = 5000, message = "상품 설명은 5000자 이하여야 합니다")
    private String description;

    @DecimalMin(value = "0.0", inclusive = false, message = "가격은 0보다 커야 합니다")
    private BigDecimal price;

    private String imageUrl;

    private Long categoryId;
}