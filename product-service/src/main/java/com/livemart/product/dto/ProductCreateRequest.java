package com.livemart.product.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ProductCreateRequest {

    @NotBlank(message = "상품명은 필수입니다")
    @Size(max = 200, message = "상품명은 200자 이하여야 합니다")
    private String name;

    @Size(max = 5000, message = "상품 설명은 5000자 이하여야 합니다")
    private String description;

    @NotNull(message = "가격은 필수입니다")
    @DecimalMin(value = "0.0", inclusive = false, message = "가격은 0보다 커야 합니다")
    private BigDecimal price;

    @NotNull(message = "재고 수량은 필수입니다")
    @Min(value = 0, message = "재고 수량은 0 이상이어야 합니다")
    private Integer stockQuantity;

    @NotNull(message = "카테고리는 필수입니다")
    private Long categoryId;

    private String imageUrl;

    @NotNull(message = "판매자 ID는 필수입니다")
    private Long sellerId;
}