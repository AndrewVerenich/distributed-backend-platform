CREATE TABLE IF NOT EXISTS users
(
    id         BIGSERIAL PRIMARY KEY,
    username   VARCHAR(100) NOT NULL UNIQUE,
    password   VARCHAR(255) NOT NULL,
    email      VARCHAR(255) NOT NULL UNIQUE,
    roles      VARCHAR(255) NOT NULL DEFAULT 'USER',
    created_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_username ON users (username);
CREATE INDEX idx_users_email ON users (email);

CREATE TABLE IF NOT EXISTS refresh_tokens
(
    id          BIGSERIAL PRIMARY KEY,
    token       VARCHAR(255) NOT NULL UNIQUE,
    user_id     BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    fingerprint VARCHAR(500) NOT NULL,
    family      VARCHAR(100) NOT NULL,
    status      VARCHAR(50)  NOT NULL DEFAULT 'ACTIVE',
    expires_at  TIMESTAMP    NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_tokens_token ON refresh_tokens (token);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_family ON refresh_tokens (family);
CREATE INDEX idx_refresh_tokens_status ON refresh_tokens (status, expires_at);

-- Demo users (password: password123)
INSERT INTO users (username, password, email, roles)
VALUES ('demo', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjzAgqehzUdHw6pK8pEhZp0q3nYPybu', 'demo@example.com', 'USER,ADMIN')
ON CONFLICT (username) DO NOTHING;

INSERT INTO users (username, password, email, roles)
VALUES ('user1', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjzAgqehzUdHw6pK8pEhZp0q3nYPybu', 'user1@example.com', 'USER')
ON CONFLICT (username) DO NOTHING;
