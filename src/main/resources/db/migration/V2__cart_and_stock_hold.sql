-- The cart is an "order" row in CART status; its lines are ordinary order_line rows.
-- stock_hold = units temporarily locked for a cart order.
-- Assumes shop.items, "order" and order_line already exist (base schema, V1).

ALTER TABLE shop.items
    ADD COLUMN IF NOT EXISTS reserved_units BIGINT NOT NULL DEFAULT 0;

ALTER TABLE shop.items
    ADD CONSTRAINT items_reserved_units_non_negative CHECK (reserved_units >= 0);

-- existing orders are already placed; new carts are inserted with status CART
ALTER TABLE "order"
    ADD COLUMN IF NOT EXISTS status       VARCHAR(16) NOT NULL DEFAULT 'PLACED',
    ADD COLUMN IF NOT EXISTS created_at   TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS last_seen_at TIMESTAMPTZ;

-- the abandoned-cart cleanup scans carts by last activity
CREATE INDEX IF NOT EXISTS order_cart_last_seen_idx
    ON "order" (last_seen_at) WHERE status = 'CART';

CREATE TABLE shop.stock_hold
(
    id             UUID PRIMARY KEY,
    order_id       UUID        NOT NULL REFERENCES "order" (id) ON DELETE CASCADE,
    item_id        UUID        NOT NULL REFERENCES shop.items (id) ON DELETE CASCADE,
    quantity       INT         NOT NULL CHECK (quantity > 0),
    created_at     TIMESTAMPTZ NOT NULL,
    expires_at     TIMESTAMPTZ NOT NULL,
    max_expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT stock_hold_order_item_uk UNIQUE (order_id, item_id)
);

-- the release job scans by expiry
CREATE INDEX stock_hold_expires_at_idx ON shop.stock_hold (expires_at);
