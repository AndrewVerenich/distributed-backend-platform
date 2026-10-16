CREATE TABLE IF NOT EXISTS accounts (
  owner_id TEXT PRIMARY KEY,
  balance  BIGINT NOT NULL CHECK (balance >= 0)
);

CREATE TABLE IF NOT EXISTS charges (
  id         UUID PRIMARY KEY,
  owner_id   TEXT NOT NULL REFERENCES accounts (owner_id),
  amount     BIGINT NOT NULL CHECK (amount > 0),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_charges_owner ON charges (owner_id);

CREATE TABLE IF NOT EXISTS idempotency_records (
  owner_id               TEXT        NOT NULL,
  idempotency_key        TEXT        NOT NULL,
  body_hash              TEXT        NOT NULL,
  status                 TEXT        NOT NULL CHECK (status IN ('started', 'completed')),
  lease_until            TIMESTAMPTZ NOT NULL,
  request_id             TEXT        NOT NULL,
  response_status        INT,
  response_content_type  TEXT,
  response_body          BYTEA,
  created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
  completed_at           TIMESTAMPTZ,
  expires_at             TIMESTAMPTZ,
  PRIMARY KEY (owner_id, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_idempotency_expires_at
  ON idempotency_records (expires_at)
  WHERE status = 'completed';

INSERT INTO accounts (owner_id, balance) VALUES ('alice', 100000)
ON CONFLICT (owner_id) DO NOTHING;
