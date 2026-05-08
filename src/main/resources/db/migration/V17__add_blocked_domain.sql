CREATE TABLE blocked_domain (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    domain VARCHAR(255) NOT NULL,
    reason VARCHAR(500) NULL,
    blocked_by_user_id BIGINT NULL,
    blocked_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_blocked_domain_domain (domain)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
