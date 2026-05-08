CREATE TABLE user_two_factor (
  user_id BIGINT NOT NULL PRIMARY KEY,
  secret VARCHAR(512) NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT FALSE,
  recovery_codes TEXT NULL,
  last_used_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  CONSTRAINT fk_user_two_factor_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
