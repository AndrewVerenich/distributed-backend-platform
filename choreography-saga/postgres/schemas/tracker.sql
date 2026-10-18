CREATE TABLE saga_projection
(
    booking_id        UUID PRIMARY KEY,
    status            VARCHAR(32)  NOT NULL,
    last_event_type   VARCHAR(100) NOT NULL,
    last_event_at     TIMESTAMPTZ  NOT NULL,
    flight_booking_id UUID         NULL,
    hotel_booking_id  UUID         NULL,
    car_rental_id     UUID         NULL,
    error_message     TEXT         NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_saga_projection_status ON saga_projection (status);
CREATE INDEX idx_saga_projection_last_event_at ON saga_projection (last_event_at);

CREATE TABLE saga_event_log
(
    id              BIGSERIAL PRIMARY KEY,
    booking_id      UUID         NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    idempotency_key UUID         NOT NULL,
    payload         JSONB        NOT NULL,
    received_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_saga_event_log_idempotency ON saga_event_log (idempotency_key);
CREATE INDEX idx_saga_event_log_booking ON saga_event_log (booking_id, received_at);
