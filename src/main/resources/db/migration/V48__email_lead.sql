-- Email leads collected via EMAIL_FORM blocks on a profile. Each row is one visitor submission.
-- ip_hash (sha256(ip + salt) at write time) lets us dedupe + rate-limit without storing raw IP.
CREATE TABLE email_lead (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    block_id BIGINT NOT NULL,
    email VARCHAR(254) NOT NULL,
    ip_hash CHAR(64) NULL,
    submitted_at DATETIME(6) NOT NULL,
    KEY idx_email_lead_user_submitted (user_id, submitted_at),
    UNIQUE KEY uk_email_lead_block_email (block_id, email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
