CREATE TABLE user_domain_block (
  id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id    BIGINT       NOT NULL,
  domain     VARCHAR(255) NOT NULL,
  created_at DATETIME(6)  NOT NULL,
  CONSTRAINT uk_user_domain_block UNIQUE (user_id, domain),
  CONSTRAINT fk_user_domain_block_user
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
