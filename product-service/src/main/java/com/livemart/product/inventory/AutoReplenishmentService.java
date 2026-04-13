package com.livemart.product.inventory;

import com.livemart.product.domain.Product;
import com.livemart.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 재고 자동 발주 시스템 (Min-Max 알고리즘)
 * 재고가 최소값 이하로 떨어지면 자동으로 발주
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AutoReplenishmentService {

    private final ProductRepository productRepository;
    private final ReplenishmentOrderService replenishmentOrderService;

    // 기본값
    private static final int DEFAULT_MIN_STOCK = 10;
    private static final int DEFAULT_MAX_STOCK = 100;
    private static final int DEFAULT_REORDER_POINT = 20;
    private static final int DEFAULT_SAFETY_STOCK = 5;

    /**
     * 자동 발주 체크 (매일 새벽 3시 실행)
     */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void checkAndReplenishStock() {
        log.info("Starting automatic stock replenishment check");

        List<Product> lowStockProducts = findLowStockProducts();

        log.info("Found {} products with low stock", lowStockProducts.size());

        for (Product product : lowStockProducts) {
            try {
                processReplenishment(product);
            } catch (Exception e) {
                log.error("Failed to process replenishment for product: {}", product.getId(), e);
            }
        }

        log.info("Automatic stock replenishment check completed");
    }

    /**
     * Min-Max 알고리즘 기반 재고 발주
     */
    @Transactional
    public ReplenishmentOrderService.ReplenishmentOrder processReplenishment(Product product) {
        int currentStock = product.getStockQuantity();
        int minStock = getMinStock(product);
        int maxStock = getMaxStock(product);
        int reorderPoint = getReorderPoint(product);
        int safetyStock = getSafetyStock(product);

        // 재고가 재발주점 이하인 경우
        if (currentStock <= reorderPoint) {
            int orderQuantity = calculateOrderQuantity(currentStock, minStock, maxStock, safetyStock);

            log.info("Creating replenishment order: productId={}, currentStock={}, orderQuantity={}",
                     product.getId(), currentStock, orderQuantity);

            ReplenishmentOrderService.ReplenishmentOrder order = replenishmentOrderService.createOrder(
                product.getId(),
                orderQuantity,
                calculateLeadTime(product)
            );

            // 알림 발송 (Kafka 이벤트)
            publishLowStockAlert(product, currentStock, orderQuantity);

            return order;
        }

        return null;
    }

    /**
     * Economic Order Quantity (EOQ) 계산
     * 최적 발주량 계산
     */
    public int calculateEOQ(Product product, double annualDemand, double orderCost, double holdingCost) {
        // EOQ = sqrt((2 * D * S) / H)
        // D: 연간 수요량, S: 주문 비용, H: 보관 비용
        return (int) Math.sqrt((2 * annualDemand * orderCost) / holdingCost);
    }

    /**
     * Safety Stock 계산 (안전 재고)
     * 리드타임 동안의 수요 변동을 고려
     */
    public int calculateSafetyStock(double avgDailyDemand, double leadTimeDays, double serviceLevel) {
        // Safety Stock = Z * σ * sqrt(LT)
        // Z: 서비스 레벨 (95% = 1.65, 99% = 2.33)
        // σ: 수요의 표준편차
        // LT: 리드타임
        double z = serviceLevel >= 0.99 ? 2.33 : 1.65;
        double stdDev = avgDailyDemand * 0.3; // 가정: 표준편차는 평균의 30%
        return (int) Math.ceil(z * stdDev * Math.sqrt(leadTimeDays));
    }

    /**
     * Reorder Point 계산 (재발주점)
     */
    public int calculateReorderPoint(double avgDailyDemand, double leadTimeDays, int safetyStock) {
        // ROP = (평균 일일 수요 * 리드타임) + 안전재고
        return (int) Math.ceil(avgDailyDemand * leadTimeDays) + safetyStock;
    }

    /**
     * ABC 분석 기반 재고 관리
     * A급: 매출 상위 20% (엄격한 관리)
     * B급: 매출 중위 30% (일반 관리)
     * C급: 매출 하위 50% (느슨한 관리)
     */
    public InventoryClass classifyProduct(Product product, double totalRevenue) {
        // 실제로는 판매 데이터 기반 계산
        double productRevenue = product.getPrice().doubleValue() * 100; // 임시
        double revenueShare = productRevenue / totalRevenue;

        if (revenueShare >= 0.7) {
            return InventoryClass.A; // 상위 20%
        } else if (revenueShare >= 0.5) {
            return InventoryClass.B; // 중위 30%
        } else {
            return InventoryClass.C; // 하위 50%
        }
    }

    // Helper methods

    private List<Product> findLowStockProducts() {
        return productRepository.findByStockQuantityLessThanEqual(DEFAULT_REORDER_POINT);
    }

    private int calculateOrderQuantity(int currentStock, int minStock, int maxStock, int safetyStock) {
        // 발주량 = MAX - 현재재고 + 안전재고
        int orderQuantity = maxStock - currentStock + safetyStock;
        return Math.max(orderQuantity, minStock);
    }

    /**
     * 최소 재고 기준값 반환.
     * 현재는 모든 제품에 동일한 기본값 적용.
     * 추후 Product 엔티티에 minStock 필드 추가 후 제품별 설정 지원 예정.
     */
    private int getMinStock(Product product) {
        return DEFAULT_MIN_STOCK;
    }

    /**
     * 최대 재고 기준값 반환.
     * 추후 제품별 설정 지원 예정.
     */
    private int getMaxStock(Product product) {
        return DEFAULT_MAX_STOCK;
    }

    /**
     * 발주 트리거 재고 기준값 반환.
     * 추후 제품별 설정 지원 예정.
     */
    private int getReorderPoint(Product product) {
        return DEFAULT_REORDER_POINT;
    }

    /**
     * 안전 재고 기준값 반환.
     * 추후 제품별 설정 지원 예정.
     */
    private int getSafetyStock(Product product) {
        return DEFAULT_SAFETY_STOCK;
    }

    /**
     * 공급 리드타임(일) 반환.
     * 추후 공급업체(Supplier) 엔티티 연동 후 공급업체별 리드타임 적용 예정.
     */
    private int calculateLeadTime(Product product) {
        return 7;
    }

    private void publishLowStockAlert(Product product, int currentStock, int orderQuantity) {
        log.warn("Low stock alert: productId={}, productName={}, currentStock={}, orderQuantity={}",
                 product.getId(), product.getName(), currentStock, orderQuantity);
        // Kafka 이벤트 발행 (실제 구현 필요)
    }

    public enum InventoryClass {
        A, // 중요도 높음 (매출 상위)
        B, // 중요도 중간
        C  // 중요도 낮음 (매출 하위)
    }

    public record ReplenishmentOrder(
        Long productId,
        int quantity,
        LocalDateTime expectedArrival,
        String status
    ) {}
}
