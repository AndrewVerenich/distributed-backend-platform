CREATE TABLE event_store
(
    id             BIGSERIAL PRIMARY KEY,
    event_id       UUID         NOT NULL,
    aggregate_id   BIGINT       NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL DEFAULT 'Account',
    event_type     VARCHAR(100) NOT NULL,
    payload        JSONB        NOT NULL,
    version        BIGINT       NOT NULL,
    created_at     TIMESTAMP(3) NOT NULL DEFAULT NOW(),
    UNIQUE (aggregate_id, version)
);

CREATE INDEX idx_event_store_aggregate_version ON event_store (aggregate_id, version);
CREATE INDEX idx_event_store_created_at ON event_store (created_at);

CREATE TABLE account_balance
(
    id          BIGSERIAL       NOT NULL PRIMARY KEY,
    owner_id   BIGINT           NOT NULL,
    balance    NUMERIC(19, 2)   NOT NULL DEFAULT 0,
    updated_at TIMESTAMP(3)     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_account_balance_owner ON account_balance (owner_id);

CREATE TABLE account_balance_snapshot
(
    id          BIGSERIAL       NOT NULL PRIMARY KEY,
    account_id  BIGINT          NOT NULL,
    balance     NUMERIC(19, 2)  NOT NULL DEFAULT 0,
    version     BIGINT          NOT NULL,
    created_at  TIMESTAMP(3)    NOT NULL DEFAULT NOW()
);