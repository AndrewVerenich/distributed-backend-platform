CREATE TABLE IF NOT EXISTS app_user (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(255),
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS product (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    price NUMERIC(12, 2) NOT NULL,
    category VARCHAR(128) NOT NULL,
    images TEXT,
    rating NUMERIC(3, 2) NOT NULL DEFAULT 0,
    review_count INT NOT NULL DEFAULT 0,
    sales_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(255),
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

INSERT INTO app_user (email, name, created_by, is_active) VALUES
    ('alice@example.com', 'Alice', 'system', true),
    ('bob@example.com', 'Bob', 'system', true);

INSERT INTO product (name, description, price, category, images, rating, review_count, sales_count, created_by, is_active) VALUES
    ('Widget A', 'Full-size widget for web', 19.99, 'gadgets', 'https://cdn.example.com/wa.jpg,https://cdn.example.com/wa2.jpg', 4.5, 120, 500, 'system', true),
    ('Widget B', 'Compact widget', 9.99, 'gadgets', 'https://cdn.example.com/wb.jpg', 4.1, 45, 200, 'system', true),
    ('Premium Kit', 'Admin-only bundle', 99.00, 'kits', 'https://cdn.example.com/kit.jpg', 4.9, 12, 30, 'admin', true);
