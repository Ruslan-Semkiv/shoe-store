-- Item price, in minor units (cents) of the line's currency, so orders have a total to pay.
ALTER TABLE shop.items
    ADD COLUMN price BIGINT NOT NULL DEFAULT 0 CHECK (price >= 0);

-- One row per payment try of a placed order.
-- PROCESSING    = a call to the provider is running; next_attempt_at is its lease (if the app dies
--                 mid-call, PaymentRetryJob picks the row up once the lease runs out)
-- RETRY_PENDING = all Feign retries failed (provider error / network timeout); the job retries it
--                 at next_attempt_at
-- SUCCEEDED / DECLINED / FAILED are final (FAILED = the job gave up too; the order is cancelled)
CREATE TABLE shop.payment
(
    id                 UUID PRIMARY KEY,
    order_id           UUID         NOT NULL REFERENCES shop."order" (id) ON DELETE CASCADE,
    amount             BIGINT       NOT NULL CHECK (amount >= 0),
    currency           VARCHAR(8)   NOT NULL,
    payment_type       VARCHAR(32)  NOT NULL,
    status             VARCHAR(16)  NOT NULL,
    external_charge_id VARCHAR(64),
    job_attempts       INT          NOT NULL DEFAULT 0,
    next_attempt_at    TIMESTAMPTZ,
    last_error         VARCHAR(500),
    created_at         TIMESTAMPTZ  NOT NULL,
    updated_at         TIMESTAMPTZ  NOT NULL
);

-- an order has at most one live payment; after a decline the customer may pay again
CREATE UNIQUE INDEX payment_order_live_uk ON shop.payment (order_id) WHERE status <> 'DECLINED';

-- the retry job scans due payments
CREATE INDEX payment_due_idx ON shop.payment (next_attempt_at)
    WHERE status IN ('PROCESSING', 'RETRY_PENDING');
