CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    oauth_provider VARCHAR(32) NOT NULL,
    oauth_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_users_email (email),
    UNIQUE KEY uk_users_provider_oauth_id (oauth_provider, oauth_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE link
    ADD COLUMN user_id BIGINT NULL,
    ADD COLUMN expires_at TIMESTAMP NULL DEFAULT NULL,
    ADD CONSTRAINT fk_link_user FOREIGN KEY (user_id) REFERENCES users(id);
