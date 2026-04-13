package com.livemart.product.repository;

import com.livemart.product.domain.ReplenishmentOrderEntity;
import com.livemart.product.domain.ReplenishmentOrderEntity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface ReplenishmentOrderRepository extends JpaRepository<ReplenishmentOrderEntity, Long> {

    List<ReplenishmentOrderEntity> findByProductIdOrderByOrderDateDesc(Long productId);

    List<ReplenishmentOrderEntity> findByStatusOrderByOrderDateDesc(OrderStatus status);

    @Query("SELECT r FROM ReplenishmentOrderEntity r " +
           "WHERE r.status IN :statuses AND r.expectedArrival < :now " +
           "ORDER BY r.expectedArrival ASC")
    List<ReplenishmentOrderEntity> findDelayedOrders(
            @Param("statuses") List<OrderStatus> statuses,
            @Param("now") LocalDateTime now);

    long countByStatus(OrderStatus status);

    @Query("SELECT COALESCE(SUM(r.totalCost), 0) FROM ReplenishmentOrderEntity r WHERE r.status = :status")
    BigDecimal sumTotalCostByStatus(@Param("status") OrderStatus status);
}
