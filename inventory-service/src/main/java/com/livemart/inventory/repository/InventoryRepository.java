package com.livemart.inventory.repository;

import com.livemart.inventory.domain.Inventory;
import com.livemart.inventory.domain.InventoryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    /**
     * 재고 단순 조회. 동시성 제어는 Redisson 분산 락으로만 처리.
     *
     * 동시성 제어 전략:
     * - Redisson 분산 락(tryLock 3s)으로 다중 인스턴스 간 Race Condition 방지
     * - JPA 비관적 락(SELECT FOR UPDATE)은 Redisson과 혼용 시 데드락 위험이 있어 제거
     *   (참고: ADR-003, ADR-004)
     */
    Optional<Inventory> findByProductId(Long productId);

    List<Inventory> findByStatus(InventoryStatus status);

    @Query("SELECT i FROM Inventory i WHERE i.availableQuantity <= i.reorderPoint AND i.status != 'DISCONTINUED'")
    List<Inventory> findNeedingReorder();

    @Query("SELECT i FROM Inventory i WHERE i.warehouseCode = :code")
    List<Inventory> findByWarehouse(@Param("code") String warehouseCode);

    boolean existsByProductId(Long productId);
}
