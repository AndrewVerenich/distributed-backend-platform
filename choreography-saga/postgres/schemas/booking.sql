CREATE TABLE bookings
(
    booking_id   UUID PRIMARY KEY,
    passenger_id BIGINT       NOT NULL,
    flight_id    VARCHAR(64)  NOT NULL,
    hotel_id     VARCHAR(64)  NOT NULL,
    car_id       VARCHAR(64)  NOT NULL,
    status       VARCHAR(32)  NOT NULL,
    error_message TEXT        NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_bookings_status ON bookings (status);
