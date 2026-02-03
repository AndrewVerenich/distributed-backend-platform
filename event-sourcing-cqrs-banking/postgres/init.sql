CREATE TABLE event_store
(
    id             BIGSERIAL PRIMARY KEY,
    event_id       UUID         NOT NULL,
    aggregate_id   BIGINT       NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL DEFAULT 'Account',
    event_type     VARCHAR(100) NOT NULL,
    payload        JSONB        NOT NULL,
    version        BIGINT       NOT NULL,
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
    UNIQUE (aggregate_id, version)
);

CREATE INDEX idx_event_store_aggregate_version ON event_store (aggregate_id, version);
CREATE INDEX idx_event_store_created_at ON event_store (created_at);

CREATE TABLE account_snapshot
(
    aggregate_id   BIGINT       NOT NULL PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL DEFAULT 'Account',
    payload        JSONB        NOT NULL,
    version        BIGINT       NOT NULL,
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE account_balance
(
    account_id BIGINT         NOT NULL PRIMARY KEY,
    owner_id   VARCHAR(255)   NOT NULL,
    balance    NUMERIC(19, 4) NOT NULL DEFAULT 0,
    version    BIGINT         NOT NULL DEFAULT 0,
    updated_at TIMESTAMPT     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_account_balance_owner ON account_balance (owner_id);

CREATE TABLE projection_processed_events
(
    event_id     UUID         NOT NULL,
    consumer     VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPT   NOT NULL DEFAULT NOW(),
    PRIMARY KEY (event_id, consumer)
);
