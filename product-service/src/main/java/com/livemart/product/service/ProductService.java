package com.livemart.product.service;

import com.livemart.common.exception.BusinessException;
import com.livemart.product.document.ProductDocument;
import com.livemart.product.domain.Category;
import com.livemart.product.domain.Product;
import com.livemart.product.domain.ProductStatus;
import com.livemart.product.dto.ProductCreateRequest;
import com.livemart.product.dto.ProductResponse;
import com.livemart.product.dto.ProductUpdateRequest;
import com.livemart.product.event.ProductEvent;
import com.livemart.product.event.StockEvent;
import com.livemart.product.repository.CategoryRepository;
import com.livemart.product.repository.ProductRepository;
import com.livemart.product.repository.ProductSearchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    // Elasticsearch: @Lazy로 주입 — ES 미기동 시에도 서비스 시작 가능
    @Autowired @Lazy
    private ProductSearchRepository productSearchRepository;

    private final KafkaTemplate<String, ProductEvent> kafkaTemplate;
    private final KafkaTemplate<String, StockEvent> stockKafkaTemplate;
    @org.springframework.beans.factory.annotation.Qualifier("dlqKafkaTemplate")
    private final KafkaTemplate<String, Object> dlqKafkaTemplate;

    private static final String PRODUCT_TOPIC = "product-events";
    private static final String STOCK_TOPIC = "stock-events";
    private static final String STOCK_ALERT_TOPIC = "stock-alert-events";
    private static final int STOCK_LOW_THRESHOLD = 5;

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
        syncToElasticsearch(savedProduct);
        publishProductEvent(savedProduct, ProductEvent.EventType.CREATED);

        log.info("상품 생성 완료: productId={}, name={}", savedProduct.getId(), savedProduct.getName());

        return ProductResponse.from(savedProduct);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "products", key = "#productId"),
            @CacheEvict(value = "product-detail", key = "#productId")
    })
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

        syncToElasticsearch(product);
        publishProductEvent(product, ProductEvent.EventType.UPDATED);

        log.info("상품 수정 완료: productId={}", productId);

        return ProductResponse.from(product);
    }

    @Cacheable(value = "product-detail", key = "#productId")
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
        try {
            return productSearchRepository
                    .findByNameContainingOrDescriptionContaining(keyword, keyword, pageable)
                    .map(ProductResponse::from);
        } catch (Exception e) {
            log.warn("Elasticsearch 검색 실패, DB fallback 사용: keyword={}, error={}", keyword, e.getMessage());
            return productRepository.searchByKeyword(keyword, pageable)
                    .map(ProductResponse::from);
        }
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "products", key = "#productId"),
            @CacheEvict(value = "product-detail", key = "#productId")
    })
    public void deleteProduct(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다"));

        productRepository.delete(product);
        productSearchRepository.deleteById(String.valueOf(productId));
        publishProductEvent(product, ProductEvent.EventType.DELETED);

        log.info("상품 삭제 완료: productId={}", productId);
    }

    // 비관적 락만 사용 (Order Service에서 이미 분산 락 획득)
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "products", key = "#productId"),
            @CacheEvict(value = "product-detail", key = "#productId")
    })
    public void updateStock(Long productId, Integer quantity) {
        // 비관적 락으로 조회
        Product product = productRepository.findByIdWithLock(productId)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다"));

        int oldStock = product.getStockQuantity();
        product.updateStock(quantity);

        syncToElasticsearch(product);
        publishProductEvent(product, ProductEvent.EventType.STOCK_CHANGED);
        publishStockEvent(productId, oldStock, quantity);

        log.info("재고 수정 완료: productId={}, oldStock={}, newStock={}", productId, oldStock, quantity);
    }

    @Transactional
    public void restoreStock(Long productId, int quantity) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> BusinessException.notFound("Product", productId));

        int oldStock = product.getStockQuantity();
        int newStock = oldStock + quantity;
        product.updateStock(newStock);

        publishStockEvent(productId, oldStock, newStock);

        log.info("Stock restored: productId={}, +{}, newStock={}", productId, quantity, newStock);
    }

    // 재고 조회 및 검증 (비관적 락 사용)
    @Transactional
    public ProductResponse getProductWithLock(Long productId) {
        Product product = productRepository.findByIdWithLock(productId)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다"));

        log.info("상품 조회 (락 사용): productId={}, stock={}", productId, product.getStockQuantity());

        return ProductResponse.from(product);
    }

    private void syncToElasticsearch(Product product) {
        try {
            if (productSearchRepository == null) return;
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
        } catch (Exception e) {
            log.warn("Elasticsearch 동기화 실패 (무시): {}", e.getMessage());
        }
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

    private void publishStockEvent(Long productId, int oldStock, int newStock) {
        StockEvent event = StockEvent.builder()
                .productId(productId)
                .oldStock(oldStock)
                .newStock(newStock)
                .occurredAt(LocalDateTime.now())
                .build();

        stockKafkaTemplate.send(STOCK_TOPIC, productId.toString(), event);
        log.info("Stock event published: productId={}, old={}, new={}", productId, oldStock, newStock);

        // 재고 부족/소진 시 stock-alert-events 발행 → notification-service 처리
        if (newStock <= 0 || (newStock <= STOCK_LOW_THRESHOLD && oldStock > STOCK_LOW_THRESHOLD)) {
            try {
                Product product = productRepository.findById(productId).orElse(null);
                if (product != null) {
                    Map<String, Object> alert = new HashMap<>();
                    alert.put("eventType", newStock <= 0 ? "OUT_OF_STOCK" : "LOW_STOCK");
                    alert.put("productId", productId);
                    alert.put("productName", product.getName());
                    alert.put("availableQuantity", newStock);
                    alert.put("warehouseCode", "DEFAULT");
                    dlqKafkaTemplate.send(STOCK_ALERT_TOPIC, productId.toString(), alert);
                    log.warn("재고 알림 발행: productId={}, newStock={}", productId, newStock);
                }
            } catch (Exception e) {
                log.error("재고 알림 발행 실패: {}", e.getMessage());
            }
        }
    }

    /**
     * DB의 모든 상품을 Elasticsearch에 일괄 인덱싱 (초기화/재구축용)
     */
    @Transactional(readOnly = true)
    public int reindexAllProducts() {
        AtomicInteger count = new AtomicInteger(0);
        int pageSize = 100;
        int page = 0;

        while (true) {
            List<Product> products = productRepository.findAll(PageRequest.of(page, pageSize)).getContent();
            if (products.isEmpty()) break;

            products.forEach(p -> {
                syncToElasticsearch(p);
                count.incrementAndGet();
            });

            log.info("Elasticsearch 재인덱싱 진행: {}건 완료", count.get());
            page++;
        }

        log.info("Elasticsearch 재인덱싱 완료: 총 {}건", count.get());
        return count.get();
    }
}