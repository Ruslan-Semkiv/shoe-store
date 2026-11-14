-- Base schema matching ItemEntity, OrderEntity and OrderLineEntity.

CREATE SCHEMA IF NOT EXISTS shop;

CREATE TABLE shop.items
(
    id           UUID PRIMARY KEY,
    name         VARCHAR(255) NOT NULL,
    colour       VARCHAR(64)  NOT NULL,
    model        VARCHAR(255) NOT NULL,
    units_number BIGINT       NOT NULL DEFAULT 0 CHECK (units_number >= 0),
    size         SMALLINT     NOT NULL
);

-- "order" is a reserved word in Postgres, so it is always quoted
CREATE TABLE "order"
(
    id           UUID PRIMARY KEY,
    total        NUMERIC(19, 2),
    payment_type VARCHAR(32),
    item_counter INT
);

CREATE TABLE order_line
(
    id         UUID PRIMARY KEY,
    quantity   INT,
    unit_price BIGINT,
    currency   VARCHAR(8),
    item_id    UUID REFERENCES shop.items (id),
    order_id   UUID REFERENCES "order" (id) ON DELETE CASCADE
);

CREATE INDEX order_line_order_id_idx ON order_line (order_id);
