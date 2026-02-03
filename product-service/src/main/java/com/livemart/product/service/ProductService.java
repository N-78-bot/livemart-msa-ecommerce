package com.livemart.product.service;

import com.livemart.product.document.ProductDocument;
import com.livemart.product.domain.Category;
import com.livemart.product.domain.Product;
import com.livemart.product.domain.ProductStatus;
import com.livemart.product.dto.ProductCreateRequest;
import com.livemart.product.dto.ProductResponse;
import com.livemart.product.dto.ProductUpdateRequest;
import com.livemart.product.event.ProductEvent;
import com.livemart.product.repository.CategoryRepository;
import com.livemart.product.repository.ProductRepository;
import com.livemart.product.repository.ProductSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductSearchRepository productSearchRepository;
    private final KafkaTemplate<String, ProductEvent> kafkaTemplate;

    private static final String PRODUCT_TOPIC = "product-events";

    @Transactional
    public ProductResponse createProduct(ProductCreateRequest request) {
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new IllegalArgumentException("카테고리를 찾을 수 없습니다"));

        Product product = Product.builder()
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .stockQuantity(request.getStockQuantity())
                .category(category)
                .status(ProductStatus.ACTIVE)
                .imageUrl(request.getImageUrl())
                .sellerId(request.getSellerId())
                .build();

        Product savedProduct = productRepository.save(product);

        // Elasticsearch 동기화
        syncToElasticsearch(savedProduct);

        // Kafka 이벤트 발행
        publishProductEvent(savedProduct, ProductEvent.EventType.CREATED);

        log.info("상품 생성 완료: productId={}, name={}", savedProduct.getId(), savedProduct.getName());

        return ProductResponse.from(savedProduct);
    }

    @Transactional
    @CacheEvict(value = "products", key = "#productId")
    public ProductResponse updateProduct(Long productId, ProductUpdateRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다"));

        if (request.getName() != null || request.getDescription() != null ||
                request.getPrice() != null || request.getImageUrl() != null) {
            product.updateInfo(
                    request.getName() != null ? request.getName() : product.getName(),
                    request.getDescription() != null ? request.getDescription() : product.getDescription(),
                    request.getPrice() != null ? request.getPrice() : product.getPrice(),
                    request.getImageUrl() != null ? request.getImageUrl() : product.getImageUrl()
            );
        }

        if (request.getCategoryId() != null) {
            Category category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new IllegalArgumentException("카테고리를 찾을 수 없습니다"));
            product.changeCategory(category);
        }

        // Elasticsearch 동기화
        syncToElasticsearch(product);

        // Kafka 이벤트 발행
        publishProductEvent(product, ProductEvent.EventType.UPDATED);

        log.info("상품 수정 완료: productId={}", productId);

        return ProductResponse.from(product);
    }

    @Cacheable(value = "products", key = "#productId")
    public ProductResponse getProduct(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다"));

        return ProductResponse.from(product);
    }

    public Page<ProductResponse> getProducts(Pageable pageable) {
        return productRepository.findAll(pageable)
                .map(ProductResponse::from);
    }

    public Page<ProductResponse> getProductsByCategory(Long categoryId, Pageable pageable) {
        return productRepository.findByCategoryId(categoryId, pageable)
                .map(ProductResponse::from);
    }

    public Page<ProductResponse> getProductsBySeller(Long sellerId, Pageable pageable) {
        return productRepository.findBySellerId(sellerId, pageable)
                .map(ProductResponse::from);
    }

    public Page<ProductResponse> searchProducts(String keyword, Pageable pageable) {
        return productRepository.searchByKeyword(keyword, pageable)
                .map(ProductResponse::from);
    }

    @Transactional
    @CacheEvict(value = "products", key = "#productId")
    public void deleteProduct(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다"));

        productRepository.delete(product);

        // Elasticsearch에서 삭제
        productSearchRepository.deleteById(String.valueOf(productId));

        // Kafka 이벤트 발행
        publishProductEvent(product, ProductEvent.EventType.DELETED);

        log.info("상품 삭제 완료: productId={}", productId);
    }

    @Transactional
    @CacheEvict(value = "products", key = "#productId")
    public void updateStock(Long productId, Integer quantity) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다"));

        product.updateStock(quantity);

        // Elasticsearch 동기화
        syncToElasticsearch(product);

        // Kafka 이벤트 발행
        publishProductEvent(product, ProductEvent.EventType.STOCK_CHANGED);

        log.info("재고 수정 완료: productId={}, newStock={}", productId, quantity);
    }

    private void syncToElasticsearch(Product product) {
        ProductDocument document = ProductDocument.builder()
                .id(String.valueOf(product.getId()))
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .stockQuantity(product.getStockQuantity())
                .categoryName(product.getCategory() != null ? product.getCategory().getName() : null)
                .status(product.getStatus().name())
                .imageUrl(product.getImageUrl())
                .sellerId(product.getSellerId())
                .build();

        productSearchRepository.save(document);
    }

    private void publishProductEvent(Product product, ProductEvent.EventType eventType) {
        ProductEvent event = ProductEvent.builder()
                .eventType(eventType)
                .productId(product.getId())
                .productName(product.getName())
                .price(product.getPrice())
                .stockQuantity(product.getStockQuantity())
                .status(product.getStatus())
                .sellerId(product.getSellerId())
                .occurredAt(LocalDateTime.now())
                .build();

        kafkaTemplate.send(PRODUCT_TOPIC, String.valueOf(product.getId()), event);

        log.info("Kafka 이벤트 발행: eventType={}, productId={}", eventType, product.getId());
    }
}