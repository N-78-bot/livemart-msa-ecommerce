-- 재고 발주 테이블 (인메모리 Map 대체 → 영속 저장)
CREATE TABLE IF NOT EXISTS replenishment_orders (
    id               BIGSERIAL PRIMARY KEY,
    product_id       BIGINT         NOT NULL,
    product_name     VARCHAR(200)   NOT NULL,
    quantity         INTEGER        NOT NULL,
    total_cost       DECIMAL(15, 2) NOT NULL,
    status           VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    order_date       TIMESTAMP      NOT NULL DEFAULT NOW(),
    expected_arrival TIMESTAMP      NOT NULL,
    lead_time_days   INTEGER        NOT NULL,
    actual_arrival   TIMESTAMP,
    tracking_number  VARCHAR(100)   NOT NULL,

    CONSTRAINT chk_replenishment_status
        CHECK (status IN ('PENDING', 'IN_TRANSIT', 'RECEIVED', 'CANCELLED')),
    CONSTRAINT chk_replenishment_quantity
        CHECK (quantity > 0),
    CONSTRAINT chk_replenishment_total_cost
        CHECK (total_cost >= 0)
);

CREATE INDEX idx_replenishment_product ON replenishment_orders(product_id);
CREATE INDEX idx_replenishment_status  ON replenishment_orders(status);
CREATE INDEX idx_replenishment_order_date ON replenishment_orders(order_date DESC);
