CREATE TABLE IF NOT EXISTS seats (
  id              TEXT PRIMARY KEY,
  venue_id        TEXT NOT NULL,
  label           TEXT NOT NULL,
  row_num         INT NOT NULL,
  col_num         INT NOT NULL,
  status          TEXT NOT NULL CHECK (status IN ('free', 'held', 'sold')),
  holder_id       TEXT,
  reservation_id  TEXT,
  hold_until      TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_seats_venue ON seats (venue_id);
CREATE INDEX IF NOT EXISTS idx_seats_held_until ON seats (status, hold_until)
  WHERE status = 'held';

CREATE TABLE IF NOT EXISTS reservations (
  id              TEXT PRIMARY KEY,
  seat_id         TEXT NOT NULL REFERENCES seats (id),
  user_id         TEXT NOT NULL,
  status          TEXT NOT NULL CHECK (status IN ('held', 'confirmed', 'expired', 'compensated')),
  hold_until      TIMESTAMPTZ,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  confirmed_at    TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_reservations_seat ON reservations (seat_id, status);

CREATE TABLE IF NOT EXISTS idempotency_records (
  owner_id               TEXT NOT NULL,
  idempotency_key        TEXT NOT NULL,
  body_hash              TEXT NOT NULL,
  status                 TEXT NOT NULL CHECK (status IN ('started', 'completed')),
  lease_until            TIMESTAMPTZ NOT NULL,
  request_id             TEXT NOT NULL,
  response_status        INT,
  response_content_type  TEXT,
  response_body          BYTEA,
  created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
  completed_at           TIMESTAMPTZ,
  expires_at             TIMESTAMPTZ,
  PRIMARY KEY (owner_id, idempotency_key)
);

CREATE TABLE IF NOT EXISTS compensation_outbox (
  id               TEXT PRIMARY KEY,
  reservation_id   TEXT NOT NULL,
  payload          TEXT NOT NULL,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  published_at     TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS payments (
  id               TEXT PRIMARY KEY,
  reservation_id   TEXT NOT NULL,
  status           TEXT NOT NULL CHECK (status IN ('pending', 'paid', 'failed')),
  delay_ms         INT NOT NULL DEFAULT 0,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  completed_at     TIMESTAMPTZ
);

-- Demo hall: 4 rows × 5 seats, all free
INSERT INTO seats (id, venue_id, label, row_num, col_num, status)
SELECT
  'hall1-r' || r || 'c' || c,
  'hall-1',
  chr(64 + r) || c,
  r,
  c,
  'free'
FROM generate_series(1, 4) AS r,
     generate_series(1, 5) AS c
ON CONFLICT (id) DO NOTHING;

-- Last-seat venue: one free seat for race demos
INSERT INTO seats (id, venue_id, label, row_num, col_num, status)
VALUES ('last-1', 'last-seat', 'A1', 1, 1, 'free')
ON CONFLICT (id) DO NOTHING;
