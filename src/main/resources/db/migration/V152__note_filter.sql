CREATE TABLE note_filter (
  id            BIGINT           NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id       BIGINT           NOT NULL,
  phrase        VARCHAR(100)     NOT NULL,
  whole_word    BOOLEAN          NOT NULL,
  contexts      INT              NOT NULL,
  filter_action VARCHAR(8)       NOT NULL,
  expires_at    DATETIME(6)      NULL,
  created_at    DATETIME(6)      NOT NULL,
  INDEX idx_note_filter_user (user_id),
  CONSTRAINT fk_note_filter_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
