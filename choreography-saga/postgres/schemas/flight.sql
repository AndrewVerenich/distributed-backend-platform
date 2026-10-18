CREATE TABLE flight_bookings
(
    flight_booking_id UUID PRIMARY KEY,
    booking_id        UUID         NOT NULL,
    flight_id         VARCHAR(64)  NOT NULL,
    passenger_id      BIGINT       NOT NULL,
    status            VARCHAR(32)  NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_flight_bookings_booking_id ON flight_bookings (booking_id);
