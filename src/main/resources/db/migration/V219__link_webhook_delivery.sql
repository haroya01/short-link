CREATE TABLE link_webhook_delivery (
  id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  hook_id         BIGINT       NOT NULL,
  click_id        BIGINT       NOT NULL,
  payload         TEXT         NOT NULL,
  status          VARCHAR(16)  NOT NULL,
  attempts        INT          NOT NULL DEFAULT 0,
  next_attempt_at DATETIME(6)  NOT NULL,
  last_status     INT          NULL,
  last_error      VARCHAR(255) NULL,
  created_at      DATETIME(6)  NOT NULL,
  updated_at      DATETIME(6)  NOT NULL,
  CONSTRAINT uk_link_webhook_delivery_click UNIQUE (hook_id, click_id),
  CONSTRAINT fk_link_webhook_delivery_hook
    FOREIGN KEY (hook_id) REFERENCES link_webhook(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_link_webhook_delivery_due ON link_webhook_delivery (status, next_attempt_at);
