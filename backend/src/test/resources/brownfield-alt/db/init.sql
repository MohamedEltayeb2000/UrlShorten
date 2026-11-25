CREATE TABLE purchase_orders (
    id    BIGINT PRIMARY KEY,
    ref   VARCHAR(40) NOT NULL UNIQUE,
    total INT NOT NULL
);
