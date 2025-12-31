CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE outbox
(
    id               BIGSERIAL PRIMARY KEY,
    partitioning_key VARCHAR(255) NOT NULL,
    type             VARCHAR(100) NOT NULL,
    payload          JSONB        NOT NULL,
    idempotency_key  UUID         NOT NULL DEFAULT gen_random_uuid(),
    status           VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
    created_at       TIMESTAMP  NOT NULL DEFAULT NOW(),
    processed_at     TIMESTAMP  NULL
);

CREATE UNIQUE INDEX idx_outbox_idempotency_key ON outbox (idempotency_key);
CREATE INDEX idx_outbox_status_created ON outbox (status, created_at);

CREATE TABLE orders
(
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT       NOT NULL,
    total_amount DOUBLE PRECISION NOT NULL,
    status       VARCHAR(50)  NOT NULL,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    delivered_at TIMESTAMP    NULL
);

CREATE INDEX idx_orders_user_id ON orders (user_id);
CREATE INDEX idx_orders_status ON orders (status);
