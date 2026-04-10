package com.livemart.inventory.service;

import com.livemart.common.event.DomainEvent;
import com.livemart.common.event.EventPublisher;
import com.livemart.inventory.domain.*;
import com.livemart.inventory.dto.*;
import com.livemart.inventory.repository.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final StockMovementRepository movementRepository;
    private final RedissonClient redissonClient;
    private final EventPublisher eventPublisher;
    private final LowStockAlertService lowStockAlertService;

    @Transactional
    public InventoryResponse createInventory(InventoryRequest.Create request) {
        if (inventoryRepository.existsByProductId(request.getProductId())) {
            throw new IllegalStateException("Inventory already exists for product: " + request.getProductId());
        }

        Inventory inventory = Inventory.builder()
                .productId(request.getProductId())
                .productName(request.getProductName())
                .warehouseCode(request.getWarehouseCode())
                .availableQuantity(request.getInitialQuantity())
                .reservedQuantity(0)
                .reorderPoint(request.getReorderPoint())
                .reorderQuantity(request.getReorderQuantity())
                .safetyStock(request.getSafetyStock())
                .build();

        inventory = inventoryRepository.save(inventory);

        recordMovement(inventory.getProductId(), StockMovement.MovementType.RESTOCK,
                request.getInitialQuantity(), 0, request.getInitialQuantity(), null, "Initial stock");

        return InventoryResponse.from(inventory);
    }

    @Transactional
    public InventoryResponse reserveStock(InventoryRequest.Reserve request) {
        RLock lock = redissonClient.getLock("inventory:lock:" + request.getProductId());
        try {
            if (!lock.tryLock(5, 10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Could not acquire lock for product: " + request.getProductId());
            }

            Inventory inventory = getInventoryByProductId(request.getProductId());
            int prev = inventory.getAvailableQuantity();
            inventory.reserve(request.getQuantity());
            inventory = inventoryRepository.save(inventory);

            recordMovement(request.getProductId(), StockMovement.MovementType.RESERVATION,
                    request.getQuantity(), prev, inventory.getAvailableQuantity(),
                    request.getOrderId(), "Stock reserved for order");

            publishStockEvent(inventory, "STOCK_RESERVED");
            lowStockAlertService.checkAndAlertIfLow(inventory);
            return InventoryResponse.from(inventory);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Thread.currentThread().interrupt();
            throw new IllegalStateException("재고 락 획득 중 인터럽트 발생", e);
        } finally {
            if (lock.isHeldByCurrentThread()) lock.unlock();
        }
    }

    @Transactional
    public InventoryResponse cancelReservation(Long productId, int quantity, String orderId) {
        Inventory inventory = getInventoryByProductId(productId);
        int prev = inventory.getAvailableQuantity();
        inventory.cancelReservation(quantity);
        inventory = inventoryRepository.save(inventory);

        recordMovement(productId, StockMovement.MovementType.RESERVATION_CANCEL,
                quantity, prev, inventory.getAvailableQuantity(), orderId, "Reservation cancelled");

        publishStockEvent(inventory, "STOCK_RELEASED");
        return InventoryResponse.from(inventory);
    }

    @Transactional
    public InventoryResponse restockInventory(InventoryRequest.Restock request) {
        Inventory inventory = getInventoryByProductId(request.getProductId());
        int prev = inventory.getAvailableQuantity();
        inventory.restock(request.getQuantity());
        inventory = inventoryRepository.save(inventory);

        recordMovement(request.getProductId(), StockMovement.MovementType.RESTOCK,
                request.getQuantity(), prev, inventory.getAvailableQuantity(), null, request.getReason());

        publishStockEvent(inventory, "STOCK_RESTOCKED");
        return InventoryResponse.from(inventory);
    }

    @Transactional(readOnly = true)
    public InventoryResponse getByProductId(Long productId) {
        return InventoryResponse.from(getInventoryByProductId(productId));
    }

    @Transactional(readOnly = true)
    public List<InventoryResponse> getLowStockItems() {
        return inventoryRepository.findByStatus(InventoryStatus.LOW_STOCK).stream()
                .map(InventoryResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<InventoryResponse> getItemsNeedingReorder() {
        return inventoryRepository.findNeedingReorder().stream()
                .map(InventoryResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public boolean checkAvailability(Long productId, int quantity) {
        return inventoryRepository.findByProductId(productId)
                .map(inv -> inv.canFulfill(quantity))
                .orElse(false);
    }

    /**
     * 원자적 재고 차감 — Redisson 분산 락으로 Race Condition 방어
     * order-service에서 직접 재고 감소 시 사용 (reserveStock과 달리 즉시 차감)
     */
    @Transactional
    public boolean decrementStock(Long productId, int quantity) {
        String lockKey = "inventory:lock:" + productId;
        RLock lock = redissonClient.getLock(lockKey);
        try {
            if (!lock.tryLock(3, 10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("재고 락 획득 실패: 상품 ID " + productId);
            }

            Inventory inventory = inventoryRepository.findByProductId(productId)
                    .orElseThrow(() -> new EntityNotFoundException("재고 정보 없음: 상품 ID " + productId));

            if (!inventory.canFulfill(quantity)) {
                return false; // 재고 부족
            }

            int prev = inventory.getAvailableQuantity();
            inventory.reserve(quantity); // availableQuantity 감소
            inventory = inventoryRepository.save(inventory);

            recordMovement(productId, StockMovement.MovementType.RESERVATION,
                    quantity, prev, inventory.getAvailableQuantity(), null, "Direct decrement");

            publishStockEvent(inventory, "STOCK_DECREMENTED");
            lowStockAlertService.checkAndAlertIfLow(inventory);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("재고 락 대기 중 인터럽트 발생", e);
        } finally {
            if (lock.isHeldByCurrentThread()) lock.unlock();
        }
    }

    private Inventory getInventoryByProductId(Long productId) {
        return inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new EntityNotFoundException("Inventory not found for product: " + productId));
    }

    private void recordMovement(Long productId, StockMovement.MovementType type,
                                int qty, int prev, int newQty, String refId, String reason) {
        movementRepository.save(StockMovement.builder()
                .productId(productId).movementType(type).quantity(qty)
                .previousQuantity(prev).newQuantity(newQty)
                .referenceId(refId).reason(reason).build());
    }

    private void publishStockEvent(Inventory inventory, String eventType) {
        eventPublisher.publish("inventory-events", DomainEvent.builder()
                .aggregateType("Inventory")
                .aggregateId(String.valueOf(inventory.getProductId()))
                .eventType(eventType)
                .payload("{\"productId\":" + inventory.getProductId() +
                         ",\"available\":" + inventory.getAvailableQuantity() +
                         ",\"status\":\"" + inventory.getStatus() + "\"}")
                .build());
    }
}
