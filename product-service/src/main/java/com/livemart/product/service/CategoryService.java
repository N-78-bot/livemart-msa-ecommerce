package com.livemart.product.service;

import com.livemart.product.domain.Category;
import com.livemart.product.dto.CategoryResponse;
import com.livemart.product.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public List<CategoryResponse> getRootCategories() {
        return categoryRepository.findByParentIsNull().stream()
                .map(CategoryResponse::from)
                .collect(Collectors.toList());
    }

    public List<CategoryResponse> getSubCategories(Long parentId) {
        return categoryRepository.findByParentId(parentId).stream()
                .map(CategoryResponse::from)
                .collect(Collectors.toList());
    }

    public CategoryResponse getCategory(Long categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("카테고리를 찾을 수 없습니다"));

        return CategoryResponse.from(category);
    }

    @Transactional
    public CategoryResponse createCategory(String name, String description, Long parentId) {
        Category parent = null;
        if (parentId != null) {
            parent = categoryRepository.findById(parentId)
                    .orElseThrow(() -> new IllegalArgumentException("상위 카테고리를 찾을 수 없습니다"));
        }

        Category category = Category.builder()
                .name(name)
                .description(description)
                .parent(parent)
                .level(parent != null ? parent.getLevel() + 1 : 0)
                .build();

        Category savedCategory = categoryRepository.save(category);

        return CategoryResponse.from(savedCategory);
    }
}