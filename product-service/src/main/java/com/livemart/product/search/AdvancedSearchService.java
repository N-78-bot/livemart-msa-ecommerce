package com.livemart.product.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import com.livemart.product.document.ProductDocument;
import com.livemart.product.dto.ProductResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Elasticsearch 고급 검색 서비스
 * Fuzzy Search, Aggregation, Autocomplete, More Like This 지원
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdvancedSearchService {

    private final ElasticsearchClient elasticsearchClient;
    private static final String INDEX_NAME = "products";

    /**
     * Fuzzy Search - 오타 허용 검색
     */
    public List<ProductResponse> fuzzySearch(String keyword, int fuzziness) {
        try {
            Query fuzzyQuery = new Query.Builder()
                .multiMatch(m -> m
                    .query(keyword)
                    .fields("name^3", "description^2", "categoryName")
                    .fuzziness(String.valueOf(fuzziness))
                    .prefixLength(1)
                )
                .build();

            SearchRequest request = SearchRequest.of(s -> s
                .index(INDEX_NAME)
                .query(fuzzyQuery)
                .size(50)
            );

            SearchResponse<ProductDocument> response = elasticsearchClient.search(request, ProductDocument.class);

            return response.hits().hits().stream()
                .map(Hit::source)
                .filter(Objects::nonNull)
                .map(ProductResponse::from)
                .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Fuzzy search 실패: keyword={}", keyword, e);
            return Collections.emptyList();
        }
    }

    /**
     * 고급 필터 검색 - 가격, 카테고리, 재고 여부 등
     */
    public List<ProductResponse> advancedSearch(SearchCriteria criteria) {
        try {
            List<Query> mustQueries = new ArrayList<>();

            // 키워드 검색 (MultiMatch + Fuzzy)
            if (criteria.getKeyword() != null && !criteria.getKeyword().isBlank()) {
                mustQueries.add(Query.of(q -> q
                    .multiMatch(m -> m
                        .query(criteria.getKeyword())
                        .fields("name^3", "description^2")
                        .type(TextQueryType.BestFields)
                        .fuzziness("AUTO")
                    )
                ));
            }

            // 가격 범위 필터
            if (criteria.getMinPrice() != null && criteria.getMaxPrice() != null) {
                final double min = criteria.getMinPrice().doubleValue();
                final double max = criteria.getMaxPrice().doubleValue();
                mustQueries.add(Query.of(q -> q
                    .range(r -> r
                        .field("price")
                        .gte(JsonData.of(min))
                        .lte(JsonData.of(max))
                    )
                ));
            } else if (criteria.getMinPrice() != null) {
                final double min = criteria.getMinPrice().doubleValue();
                mustQueries.add(Query.of(q -> q
                    .range(r -> r
                        .field("price")
                        .gte(JsonData.of(min))
                    )
                ));
            } else if (criteria.getMaxPrice() != null) {
                final double max = criteria.getMaxPrice().doubleValue();
                mustQueries.add(Query.of(q -> q
                    .range(r -> r
                        .field("price")
                        .lte(JsonData.of(max))
                    )
                ));
            }

            // 카테고리 필터
            if (criteria.getCategoryId() != null) {
                final long catId = criteria.getCategoryId();
                mustQueries.add(Query.of(q -> q
                    .term(t -> t
                        .field("categoryId")
                        .value(catId)
                    )
                ));
            }

            // 재고 있는 상품만
            if (criteria.isInStockOnly()) {
                mustQueries.add(Query.of(q -> q
                    .range(r -> r
                        .field("stockQuantity")
                        .gt(JsonData.of(0))
                    )
                ));
            }

            // ACTIVE 상품만
            mustQueries.add(Query.of(q -> q
                .term(t -> t
                    .field("status")
                    .value("ACTIVE")
                )
            ));

            BoolQuery boolQuery = BoolQuery.of(b -> b.must(mustQueries));

            String sortField = criteria.getSortBy() != null ? criteria.getSortBy() : "_score";
            SortOrder sortOrder = criteria.getSortOrder() != null ? criteria.getSortOrder() : SortOrder.Desc;

            int from = criteria.getPage() * criteria.getSize();
            int size = criteria.getSize();

            SearchRequest request;
            if ("_score".equals(sortField)) {
                // _score 정렬은 score sort 사용
                request = SearchRequest.of(s -> s
                    .index(INDEX_NAME)
                    .query(q -> q.bool(boolQuery))
                    .from(from)
                    .size(size)
                    .sort(so -> so.score(sc -> sc.order(sortOrder)))
                );
            } else {
                request = SearchRequest.of(s -> s
                    .index(INDEX_NAME)
                    .query(q -> q.bool(boolQuery))
                    .from(from)
                    .size(size)
                    .sort(so -> so.field(f -> f.field(sortField).order(sortOrder)))
                );
            }

            SearchResponse<ProductDocument> response = elasticsearchClient.search(request, ProductDocument.class);

            return response.hits().hits().stream()
                .map(Hit::source)
                .filter(Objects::nonNull)
                .map(ProductResponse::from)
                .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("고급 검색 실패: criteria={}", criteria, e);
            return Collections.emptyList();
        }
    }

    /**
     * Aggregation - 카테고리별 상품 수, 가격 통계
     */
    public Map<String, Object> getAggregations() {
        try {
            SearchRequest request = SearchRequest.of(s -> s
                .index(INDEX_NAME)
                .size(0)
                .aggregations("categories", Aggregation.of(a -> a
                    .terms(t -> t.field("categoryName"))
                ))
                .aggregations("price_stats", Aggregation.of(a -> a
                    .stats(st -> st.field("price"))
                ))
            );

            SearchResponse<Void> response = elasticsearchClient.search(request, Void.class);

            Map<String, Object> result = new HashMap<>();
            response.aggregations().forEach((key, value) -> result.put(key, value._get()));

            return result;

        } catch (Exception e) {
            log.error("Aggregation 실패", e);
            return Collections.emptyMap();
        }
    }

    /**
     * Autocomplete - 검색어 자동완성 (prefix 기반)
     */
    public List<String> autocomplete(String prefix) {
        try {
            Query prefixQuery = Query.of(q -> q
                .prefix(p -> p
                    .field("name")
                    .value(prefix.toLowerCase())
                )
            );

            SearchRequest request = SearchRequest.of(s -> s
                .index(INDEX_NAME)
                .query(prefixQuery)
                .size(10)
                .source(so -> so
                    .filter(f -> f
                        .includes("name")
                    )
                )
            );

            SearchResponse<ProductDocument> response = elasticsearchClient.search(request, ProductDocument.class);

            return response.hits().hits().stream()
                .map(Hit::source)
                .filter(Objects::nonNull)
                .map(ProductDocument::getName)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("자동완성 실패: prefix={}", prefix, e);
            return Collections.emptyList();
        }
    }

    /**
     * 유사 상품 추천 (More Like This)
     */
    public List<ProductResponse> getRelatedProducts(Long productId, int size) {
        try {
            Query moreLikeThisQuery = Query.of(q -> q
                .moreLikeThis(m -> m
                    .fields("name", "description", "categoryName")
                    .like(l -> l.document(d -> d
                        .index(INDEX_NAME)
                        .id(productId.toString())
                    ))
                    .minTermFreq(1)
                    .maxQueryTerms(12)
                )
            );

            SearchRequest request = SearchRequest.of(s -> s
                .index(INDEX_NAME)
                .query(moreLikeThisQuery)
                .size(size)
            );

            SearchResponse<ProductDocument> response = elasticsearchClient.search(request, ProductDocument.class);

            return response.hits().hits().stream()
                .map(Hit::source)
                .filter(Objects::nonNull)
                .map(ProductResponse::from)
                .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("유사 상품 검색 실패: productId={}", productId, e);
            return Collections.emptyList();
        }
    }
}
