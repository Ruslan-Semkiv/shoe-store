-- The price's currency belongs to the item; order lines copy it instead of taking the
-- customer's choice (which let a customer pay 1250 kopiyok instead of 1250 euro cents).
ALTER TABLE shop.items
    ADD COLUMN currency VARCHAR(8) NOT NULL DEFAULT 'EUR';
