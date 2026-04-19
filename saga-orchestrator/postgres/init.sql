CREATE TABLE saga_instance
(
    id           BIGSERIAL PRIMARY KEY,
    saga_id      UUID         NOT NULL UNIQUE,
    saga_type    VARCHAR(100) NOT NULL,
    status       VARCHAR(30)  NOT NULL DEFAULT 'STARTED',
    current_step VARCHAR(100),
    payload      JSONB        NOT NULL,
    created_at   TIMESTAMP(3) NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP(3) NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMP(3)
);

CREATE INDEX idx_saga_instance_status ON saga_instance (status);
CREATE INDEX idx_saga_instance_type ON saga_instance (saga_type);
CREATE INDEX idx_saga_instance_created ON saga_instance (created_at DESC);

CREATE TABLE saga_step
(
    id               BIGSERIAL PRIMARY KEY,
    saga_instance_id BIGINT       NOT NULL REFERENCES saga_instance (id),
    step_name        VARCHAR(100) NOT NULL,
    step_type        VARCHAR(30)  NOT NULL,
    step_order       INT          NOT NULL,
    status           VARCHAR(30)  NOT NULL DEFAULT 'PENDING',
    command_payload  JSONB,
    reply_payload    JSONB,
    error_message    TEXT,
    retry_count      INT          NOT NULL DEFAULT 0,
    started_at       TIMESTAMP(3),
    completed_at     TIMESTAMP(3),
    UNIQUE (saga_instance_id, step_name)
);

CREATE INDEX idx_saga_step_instance ON saga_step (saga_instance_id);
CREATE INDEX idx_saga_step_status ON saga_step (status);
