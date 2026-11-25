-- Schema for the demo link service.
CREATE TABLE links (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    code       VARCHAR(16)   NOT NULL UNIQUE,
    target_url VARCHAR(2048) NOT NULL,
    created_at TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_links_code ON links (code);
