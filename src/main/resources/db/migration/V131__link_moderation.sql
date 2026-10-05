CREATE TABLE link_moderation (
  link_id      BIGINT      NOT NULL PRIMARY KEY,
  reason       VARCHAR(32) NOT NULL,
  disabled_by  BIGINT      NULL,
  disabled_at  DATETIME(6) NOT NULL,
  created_at   DATETIME(6) NOT NULL,
  updated_at   DATETIME(6) NOT NULL,
  CONSTRAINT fk_link_moderation_link
    FOREIGN KEY (link_id) REFERENCES link(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
