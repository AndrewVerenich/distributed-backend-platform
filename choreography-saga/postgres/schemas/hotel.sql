CREATE TABLE hotel_bookings
(
    hotel_booking_id UUID PRIMARY KEY,
    booking_id       UUID         NOT NULL,
    hotel_id         VARCHAR(64)  NOT NULL,
    guest_id         BIGINT       NOT NULL,
    status           VARCHAR(32)  NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_hotel_bookings_booking_id ON hotel_bookings (booking_id);
