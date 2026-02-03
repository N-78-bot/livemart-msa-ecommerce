package com.livemart.product.repository;

import com.livemart.product.document.ProductDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductSearchRepository extends ElasticsearchRepository<ProductDocument, String> {

    Page<ProductDocument> findByNameContainingOrDescriptionContaining(
            String name, String description, Pageable pageable);

    Page<ProductDocument> findByCategoryName(String categoryName, Pageable pageable);

    Page<ProductDocument> findByPriceBetween(Double minPrice, Double maxPrice, Pageable pageable);
}