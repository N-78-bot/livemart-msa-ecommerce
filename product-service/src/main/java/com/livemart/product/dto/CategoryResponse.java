package com.livemart.product.dto;

import com.livemart.product.domain.Category;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
@AllArgsConstructor
public class CategoryResponse {

    private Long id;
    private String name;
    private String description;
    private Long parentId;
    private Integer level;
    private List<CategoryResponse> children;

    public static CategoryResponse from(Category category) {
        return CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .description(category.getDescription())
                .parentId(category.getParent() != null ? category.getParent().getId() : null)
                .level(category.getLevel())
                .children(category.getChildren().stream()
                        .map(CategoryResponse::from)
                        .collect(Collectors.toList()))
                .build();
    }
}