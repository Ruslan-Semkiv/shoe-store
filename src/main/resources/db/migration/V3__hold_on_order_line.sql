-- One line per item in the cart; the line carries its own stock hold, so stock_hold goes away.
-- quantity          = what the customer wants (kept for weeks)
-- reserved_quantity = units locked for them right now (minutes; freed by StockHoldReleaseJob)

-- the entities map these tables to the shop schema, next to shop.items
ALTER TABLE "order" SET SCHEMA shop;
ALTER TABLE order_line SET SCHEMA shop;

ALTER TABLE shop.order_line
    ADD COLUMN reserved_quantity  INT NOT NULL DEFAULT 0 CHECK (reserved_quantity >= 0),
    ADD COLUMN reserved_until     TIMESTAMPTZ,
    ADD COLUMN reserved_max_until TIMESTAMPTZ;

-- carry the live holds over to their lines
UPDATE shop.order_line l
SET reserved_quantity  = h.quantity,
    reserved_until     = h.expires_at,
    reserved_max_until = h.max_expires_at
FROM shop.stock_hold h
WHERE h.order_id = l.order_id
  AND h.item_id = l.item_id;

-- a hold without a line has nowhere to go: give its units back
UPDATE shop.items i
SET reserved_units = GREATEST(i.reserved_units - o.qty, 0)
FROM (SELECT h.item_id, SUM(h.quantity) AS qty
      FROM shop.stock_hold h
      WHERE NOT EXISTS (SELECT 1
                        FROM shop.order_line l
                        WHERE l.order_id = h.order_id
                          AND l.item_id = h.item_id)
      GROUP BY h.item_id) o
WHERE i.id = o.item_id;

DROP TABLE shop.stock_hold;

-- the release job scans held lines by expiry
CREATE INDEX order_line_reserved_until_idx
    ON shop.order_line (reserved_until) WHERE reserved_quantity > 0;
