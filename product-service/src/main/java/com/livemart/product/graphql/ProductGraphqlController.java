package com.livemart.product.graphql;

import com.livemart.common.exception.BusinessException;
import com.livemart.product.domain.Category;
import com.livemart.product.domain.Product;
import com.livemart.product.domain.ProductStatus;
import com.livemart.product.repository.CategoryRepository;
import com.livemart.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.graphql.data.method.annotation.*;
import org.springframework.stereotype.Controller;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * GraphQL 컨트롤러 - 상품 및 카테고리 조회/변경
 * Spring for GraphQL을 사용한 유연한 데이터 조회 API
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class ProductGraphqlController {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    // ======================== Query ========================

    @QueryMapping
    public Product product(@Argument Long id) {
        log.info("GraphQL 상품 조회: id={}", id);
        return productRepository.findByIdWithCategory(id)
                .orElse(null);
    }

    @QueryMapping
    public Map<String, Object> products(@Argument int page, @Argument int size) {
        log.info("GraphQL 상품 목록 조회: page={}, size={}", page, size);
        Page<Product> productPage = productRepository.findAll(PageRequest.of(page, size));
        return toPageMap(productPage);
    }

    @QueryMapping
    public Map<String, Object> productsByCategory(@Argument Long categoryId,
                                                   @Argument int page, @Argument int size) {
        log.info("GraphQL 카테고리별 상품 조회: categoryId={}", categoryId);
        Page<Product> productPage = productRepository.findByCategoryId(categoryId, PageRequest.of(page, size));
        return toPageMap(productPage);
    }

    @QueryMapping
    public Map<String, Object> productsBySeller(@Argument Long sellerId,
                                                 @Argument int page, @Argument int size) {
        log.info("GraphQL 판매자별 상품 조회: sellerId={}", sellerId);
        Page<Product> productPage = productRepository.findBySellerId(sellerId, PageRequest.of(page, size));
        return toPageMap(productPage);
    }

    @QueryMapping
    public Map<String, Object> searchProducts(@Argument String keyword,
                                               @Argument int page, @Argument int size) {
        log.info("GraphQL 상품 검색: keyword={}", keyword);
        Page<Product> productPage = productRepository.searchByKeyword(keyword, PageRequest.of(page, size));
        return toPageMap(productPage);
    }

    @QueryMapping
    public List<Category> categories() {
        log.info("GraphQL 카테고리 목록 조회");
        return categoryRepository.findAll();
    }

    @QueryMapping
    public Category category(@Argument Long id) {
        log.info("GraphQL 카테고리 조회: id={}", id);
        return categoryRepository.findById(id).orElse(null);
    }

    // ======================== Mutation ========================

    @MutationMapping
    public Product createProduct(@Argument("input") Map<String, Object> input) {
        log.info("GraphQL 상품 생성: {}", input);

        Category category = categoryRepository.findById(Long.parseLong(input.get("categoryId").toString()))
                .orElseThrow(() -> BusinessException.notFound("Category", input.get("categoryId")));

        Product product = Product.builder()
                .name((String) input.get("name"))
                .description((String) input.get("description"))
                .price(new BigDecimal(input.get("price").toString()))
                .stockQuantity((Integer) input.get("stockQuantity"))
                .category(category)
                .status(ProductStatus.ACTIVE)
                .imageUrl((String) input.get("imageUrl"))
                .sellerId(Long.parseLong(input.get("sellerId").toString()))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return productRepository.save(product);
    }

    @MutationMapping
    public Product updateProduct(@Argument Long id, @Argument("input") Map<String, Object> input) {
        log.info("GraphQL 상품 수정: id={}, input={}", id, input);

        Product product = productRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Product", id));

        String name = input.get("name") != null ? (String) input.get("name") : product.getName();
        String description = input.get("description") != null ? (String) input.get("description") : product.getDescription();
        BigDecimal price = input.get("price") != null ? new BigDecimal(input.get("price").toString()) : product.getPrice();
        String imageUrl = input.get("imageUrl") != null ? (String) input.get("imageUrl") : product.getImageUrl();

        product.updateInfo(name, description, price, imageUrl);
        return productRepository.save(product);
    }

    @MutationMapping
    public Product updateStock(@Argument Long id, @Argument int quantity) {
        log.info("GraphQL 재고 업데이트: id={}, quantity={}", id, quantity);

        Product product = productRepository.findByIdWithLock(id)
                .orElseThrow(() -> BusinessException.notFound("Product", id));

        product.updateStock(quantity);
        return productRepository.save(product);
    }

    @MutationMapping
    public Product changeProductStatus(@Argument Long id, @Argument ProductStatus status) {
        log.info("GraphQL 상품 상태 변경: id={}, status={}", id, status);

        Product product = productRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Product", id));

        product.changeStatus(status);
        return productRepository.save(product);
    }

    // ======================== Field Resolver ========================

    @SchemaMapping(typeName = "Category", field = "products")
    public List<Product> getCategoryProducts(Category category) {
        return productRepository.findByCategoryId(category.getId(), PageRequest.of(0, 100)).getContent();
    }

    // ======================== Helper ========================

    private Map<String, Object> toPageMap(Page<Product> page) {
        return Map.of(
                "content", page.getContent(),
                "totalElements", (int) page.getTotalElements(),
                "totalPages", page.getTotalPages(),
                "currentPage", page.getNumber(),
                "size", page.getSize(),
                "hasNext", page.hasNext(),
                "hasPrevious", page.hasPrevious()
        );
    }
}
