-- The order total is in minor units (cents), like order_line.unit_price and OrderEntity.total (Long).
ALTER TABLE shop."order"
    ALTER COLUMN total TYPE BIGINT USING ROUND(total)::BIGINT;
