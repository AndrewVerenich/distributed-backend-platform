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
