CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE tenants (
    id      VARCHAR(64) PRIMARY KEY,
    api_key VARCHAR(128) NOT NULL UNIQUE,
    name    VARCHAR(255) NOT NULL
);

CREATE TABLE webhooks (
    id                              VARCHAR(64) PRIMARY KEY,
    tenant_id                       VARCHAR(64) NOT NULL REFERENCES tenants (id),
    event_type                      VARCHAR(128) NOT NULL,
    url                             TEXT NOT NULL,
    signing_secret                  VARCHAR(255) NOT NULL,
    previous_signing_secret         VARCHAR(255),
    previous_secret_valid_until     TIMESTAMPTZ,
    status                          VARCHAR(32) NOT NULL DEFAULT 'active',
    consecutive_transport_failures  INT NOT NULL DEFAULT 0,
    created_at                      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_webhooks_tenant_type ON webhooks (tenant_id, event_type)
    WHERE status = 'active';

-- Delivery outbox for Debezium EventRouter (columns aligned with outbox SMT config).
CREATE TABLE events (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id         VARCHAR(128) NOT NULL UNIQUE,
    tenant_id        VARCHAR(64) NOT NULL,
    event_type       VARCHAR(128) NOT NULL,
    partitioning_key VARCHAR(255) NOT NULL,
    type             VARCHAR(100) NOT NULL DEFAULT 'event',
    payload          JSONB NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_events_created ON events (created_at);

CREATE TABLE delivery_attempts (
    id              VARCHAR(64) PRIMARY KEY,
    event_id        VARCHAR(128) NOT NULL,
    webhook_id      VARCHAR(64) NOT NULL REFERENCES webhooks (id),
    tenant_id       VARCHAR(64) NOT NULL,
    partition_key   VARCHAR(255) NOT NULL,
    event_type      VARCHAR(128) NOT NULL,
    body            JSONB NOT NULL,
    status          VARCHAR(32) NOT NULL DEFAULT 'pending',
    attempt_count   INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    claimed_at      TIMESTAMPTZ,
    last_error      TEXT,
    idempotency_key VARCHAR(255) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (event_id, webhook_id)
);

CREATE INDEX idx_delivery_claim ON delivery_attempts (status, next_attempt_at);
CREATE INDEX idx_delivery_order ON delivery_attempts (partition_key, webhook_id, created_at);

INSERT INTO tenants (id, api_key, name) VALUES
    ('ten_demo', 'demo-api-key', 'Demo Tenant'),
    ('ten_other', 'other-api-key', 'Other Tenant');

-- Seed webhooks → demo-receiver paths (secrets mirrored in demo-receiver config).
INSERT INTO webhooks (id, tenant_id, event_type, url, signing_secret) VALUES
    ('wh-ok', 'ten_demo', 'order.paid', 'http://demo-receiver:8243/hooks/ok', 'secret-ok'),
    ('wh-fail', 'ten_demo', 'order.paid', 'http://demo-receiver:8243/hooks/fail', 'secret-fail'),
    ('wh-bad', 'ten_demo', 'order.poison', 'http://demo-receiver:8243/hooks/bad', 'secret-bad'),
    ('wh-gone', 'ten_demo', 'order.gone', 'http://demo-receiver:8243/hooks/gone', 'secret-gone'),
    ('wh-slow', 'ten_demo', 'order.paid', 'http://demo-receiver:8243/hooks/slow', 'secret-slow'),
    ('wh-ok-only', 'ten_demo', 'order.isolated', 'http://demo-receiver:8243/hooks/ok', 'secret-ok'),
    ('wh-slow-iso', 'ten_demo', 'order.isolated', 'http://demo-receiver:8243/hooks/slow', 'secret-slow');
