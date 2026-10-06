CREATE TABLE federation_preference (
  user_id        BIGINT       NOT NULL PRIMARY KEY,
  enabled        BOOLEAN      NOT NULL,
  notice_seen_at DATETIME(6)  NULL,
  created_at     DATETIME(6)  NOT NULL,
  updated_at     DATETIME(6)  NOT NULL,
  CONSTRAINT fk_federation_preference_user
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
