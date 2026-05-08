CREATE TABLE link_webhook (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  link_id BIGINT NOT NULL,
  url VARCHAR(2048) NOT NULL,
  secret CHAR(64) NOT NULL,
  name VARCHAR(100) NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME(3) NOT NULL,
  last_called_at DATETIME(3) NULL,
  last_status_code INT NULL,
  last_error VARCHAR(500) NULL,
  KEY idx_link_webhook_link (link_id, enabled),
  CONSTRAINT fk_link_webhook_link FOREIGN KEY (link_id) REFERENCES link(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
