CREATE TABLE note_schedule (
  id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id    BIGINT       NOT NULL,
  publish_at DATETIME(6)  NOT NULL,
  draft      JSON         NOT NULL,
  failure    VARCHAR(64)  NULL,
  created_at DATETIME(6)  NOT NULL,
  KEY idx_note_schedule_due (publish_at),
  KEY idx_note_schedule_user (user_id, publish_at),
  CONSTRAINT fk_note_schedule_user
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
