CREATE TABLE IF NOT EXISTS clients (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(200)             NOT NULL,
    email      VARCHAR(254)             NOT NULL,
    country    CHAR(2)                  NOT NULL,
    status     VARCHAR(20)              NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
