CREATE TABLE products (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(120)   NOT NULL,
    description VARCHAR(1000),
    price       NUMERIC(12, 2) NOT NULL CHECK (price > 0),
    quantity    INTEGER        NOT NULL CHECK (quantity >= 0),
    created_at  TIMESTAMP      NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP      NOT NULL DEFAULT now()
);

CREATE INDEX idx_products_name ON products (name);
