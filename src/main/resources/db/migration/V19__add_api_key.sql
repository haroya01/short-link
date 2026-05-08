CREATE TABLE api_key (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    key_prefix VARCHAR(20) NOT NULL,
    key_hash CHAR(64) NOT NULL,
    name VARCHAR(100) NULL,
    last_used_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    UNIQUE KEY uk_api_key_hash (key_hash),
    KEY idx_api_key_user (user_id),
    CONSTRAINT fk_api_key_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
