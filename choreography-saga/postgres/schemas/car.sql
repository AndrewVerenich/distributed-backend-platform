CREATE TABLE car_rentals
(
    car_rental_id UUID PRIMARY KEY,
    booking_id    UUID         NOT NULL,
    car_id        VARCHAR(64)  NOT NULL,
    driver_id     BIGINT       NOT NULL,
    status        VARCHAR(32)  NOT NULL,
    retry_count   INT          NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_car_rentals_booking_id ON car_rentals (booking_id);
