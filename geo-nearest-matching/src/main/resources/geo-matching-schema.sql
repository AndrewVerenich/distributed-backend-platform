CREATE TABLE IF NOT EXISTS providers (
  id TEXT PRIMARY KEY,
  status TEXT NOT NULL,
  lng DOUBLE PRECISION,
  lat DOUBLE PRECISION,
  last_seen_at TIMESTAMPTZ,
  current_request_id TEXT NULL
);

CREATE INDEX IF NOT EXISTS idx_providers_status_last_seen
  ON providers (status, last_seen_at);

CREATE TABLE IF NOT EXISTS requests (
  id TEXT PRIMARY KEY,
  status TEXT NOT NULL,
  lng DOUBLE PRECISION NOT NULL,
  lat DOUBLE PRECISION NOT NULL,
  radius_m DOUBLE PRECISION,
  provider_id TEXT NULL,
  distance_m DOUBLE PRECISION,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_requests_status ON requests (status);
