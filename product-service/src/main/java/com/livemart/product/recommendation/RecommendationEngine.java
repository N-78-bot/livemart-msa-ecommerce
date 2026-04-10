package com.livemart.product.recommendation;

import com.livemart.product.dto.ProductResponse;
import com.livemart.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * AI 기반 상품 추천 엔진
 * 협업 필터링 (Collaborative Filtering) 알고리즘 구현
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendationEngine {

    private final ProductRepository productRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String USER_VIEWS_KEY = "user:views:";
    private static final String PRODUCT_VIEWS_KEY = "product:views:";
    private static final String RECOMMENDATIONS_KEY = "recommendations:";

    /**
     * 사용자 기반 협업 필터링 (User-Based Collaborative Filtering)
     * 비슷한 사용자들이 구매한 상품 추천
     */
    public List<ProductResponse> getUserBasedRecommendations(Long userId, int limit) {
        try {
            // 1. 사용자의 최근 조회 상품 가져오기
            Set<Long> userViewedProducts = getUserViewHistory(userId);
            if (userViewedProducts.isEmpty()) {
                return getPopularProducts(limit);
            }

            // 2. 유사 사용자 찾기 (같은 상품을 본 다른 사용자들)
            Map<Long, Double> similarUsers = findSimilarUsers(userId, userViewedProducts);

            // 3. 유사 사용자들이 본 상품 중 내가 안 본 상품 추천
            Map<Long, Double> recommendationScores = new HashMap<>();

            for (Map.Entry<Long, Double> entry : similarUsers.entrySet()) {
                Long similarUserId = entry.getKey();
                Double similarity = entry.getValue();

                Set<Long> similarUserProducts = getUserViewHistory(similarUserId);
                for (Long productId : similarUserProducts) {
                    if (!userViewedProducts.contains(productId)) {
                        recommendationScores.merge(productId, similarity, Double::sum);
                    }
                }
            }

            // 4. 점수 순으로 정렬하여 반환
            return recommendationScores.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(limit)
                .map(e -> getProductById(e.getKey()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("User-based recommendation failed for userId: {}", userId, e);
            return getPopularProducts(limit);
        }
    }

    /**
     * 아이템 기반 협업 필터링 (Item-Based Collaborative Filtering)
     * 유사한 상품 추천
     */
    public List<ProductResponse> getItemBasedRecommendations(Long productId, int limit) {
        try {
            // Redis 캐시 확인
            String cacheKey = RECOMMENDATIONS_KEY + productId;
            List<ProductResponse> cached = (List<ProductResponse>) redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return cached.stream().limit(limit).collect(Collectors.toList());
            }

            // 1. 이 상품을 본 사용자들 가져오기
            Set<Long> usersWhoViewedProduct = getProductViewers(productId);

            // 2. 그 사용자들이 본 다른 상품들
            Map<Long, Integer> coViewedProducts = new HashMap<>();

            for (Long userId : usersWhoViewedProduct) {
                Set<Long> userProducts = getUserViewHistory(userId);
                for (Long otherProductId : userProducts) {
                    if (!otherProductId.equals(productId)) {
                        coViewedProducts.merge(otherProductId, 1, Integer::sum);
                    }
                }
            }

            // 3. 공동 조회 수 기준 정렬
            List<ProductResponse> recommendations = coViewedProducts.entrySet().stream()
                .sorted(Map.Entry.<Long, Integer>comparingByValue().reversed())
                .limit(limit)
                .map(e -> getProductById(e.getKey()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

            // 캐시 저장 (10분)
            redisTemplate.opsForValue().set(cacheKey, recommendations, 10, TimeUnit.MINUTES);

            return recommendations;

        } catch (Exception e) {
            log.error("Item-based recommendation failed for productId: {}", productId, e);
            return Collections.emptyList();
        }
    }

    /**
     * 콘텐츠 기반 필터링 (Content-Based Filtering)
     * 상품 속성 기반 추천 (카테고리, 가격대 등)
     */
    public List<ProductResponse> getContentBasedRecommendations(Long productId, int limit) {
        try {
            var product = productRepository.findById(productId).orElse(null);
            if (product == null) {
                return Collections.emptyList();
            }

            // 같은 카테고리, ±30% 가격대 상품 추천 (DB 쿼리로 OOM 방지)
            if (product.getCategory() == null) return Collections.emptyList();
            BigDecimal price = product.getPrice();
            BigDecimal margin = price.multiply(BigDecimal.valueOf(0.3));
            return productRepository.findSimilarProducts(
                    product.getCategory().getId(), productId,
                    price.subtract(margin), price.add(margin),
                    price, PageRequest.of(0, limit)
                ).stream()
                .map(ProductResponse::from)
                .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Content-based recommendation failed for productId: {}", productId, e);
            return Collections.emptyList();
        }
    }

    /**
     * 하이브리드 추천 (Hybrid Recommendation)
     * 협업 필터링 + 콘텐츠 기반 필터링 조합
     */
    public List<ProductResponse> getHybridRecommendations(Long userId, Long productId, int limit) {
        List<ProductResponse> userBased = getUserBasedRecommendations(userId, limit / 2);
        List<ProductResponse> contentBased = getContentBasedRecommendations(productId, limit / 2);

        Set<Long> seenIds = new HashSet<>();
        List<ProductResponse> hybrid = new ArrayList<>();

        // 교차로 섞기
        for (int i = 0; i < Math.max(userBased.size(), contentBased.size()); i++) {
            if (i < userBased.size() && seenIds.add(userBased.get(i).getId())) {
                hybrid.add(userBased.get(i));
            }
            if (i < contentBased.size() && seenIds.add(contentBased.get(i).getId())) {
                hybrid.add(contentBased.get(i));
            }
        }

        return hybrid.stream().limit(limit).collect(Collectors.toList());
    }

    /**
     * 사용자 조회 기록 저장
     */
    public void recordUserView(Long userId, Long productId) {
        String userKey = USER_VIEWS_KEY + userId;
        String productKey = PRODUCT_VIEWS_KEY + productId;

        redisTemplate.opsForSet().add(userKey, productId);
        redisTemplate.opsForSet().add(productKey, userId);

        // 30일 TTL
        redisTemplate.expire(userKey, 30, TimeUnit.DAYS);
        redisTemplate.expire(productKey, 30, TimeUnit.DAYS);
    }

    // Helper methods
    private Set<Long> getUserViewHistory(Long userId) {
        Set<Object> views = redisTemplate.opsForSet().members(USER_VIEWS_KEY + userId);
        return views != null ? views.stream().map(o -> (Long) o).collect(Collectors.toSet()) : Collections.emptySet();
    }

    private Set<Long> getProductViewers(Long productId) {
        Set<Object> viewers = redisTemplate.opsForSet().members(PRODUCT_VIEWS_KEY + productId);
        return viewers != null ? viewers.stream().map(o -> (Long) o).collect(Collectors.toSet()) : Collections.emptySet();
    }

    private Map<Long, Double> findSimilarUsers(Long userId, Set<Long> userProducts) {
        Map<Long, Double> similarities = new HashMap<>();

        for (Long productId : userProducts) {
            Set<Long> otherUsers = getProductViewers(productId);
            for (Long otherUserId : otherUsers) {
                if (!otherUserId.equals(userId)) {
                    Set<Long> otherUserProducts = getUserViewHistory(otherUserId);
                    double similarity = calculateJaccardSimilarity(userProducts, otherUserProducts);
                    similarities.merge(otherUserId, similarity, Double::sum);
                }
            }
        }

        return similarities;
    }

    private double calculateJaccardSimilarity(Set<Long> set1, Set<Long> set2) {
        Set<Long> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);

        Set<Long> union = new HashSet<>(set1);
        union.addAll(set2);

        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    private ProductResponse getProductById(Long productId) {
        return productRepository.findById(productId)
            .map(ProductResponse::from)
            .orElse(null);
    }

    private List<ProductResponse> getPopularProducts(int limit) {
        return productRepository.findTopByOrderByStockQuantityDesc(PageRequest.of(0, limit))
            .stream()
            .map(ProductResponse::from)
            .collect(Collectors.toList());
    }
}
