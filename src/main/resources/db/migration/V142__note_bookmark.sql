CREATE TABLE note_bookmark (
  id         BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
  note_id    BIGINT      NOT NULL,
  user_id    BIGINT      NOT NULL,
  created_at DATETIME(6) NOT NULL,
  CONSTRAINT uk_note_bookmark UNIQUE (note_id, user_id),
  KEY idx_note_bookmark_user (user_id, id),
  CONSTRAINT fk_note_bookmark_note FOREIGN KEY (note_id) REFERENCES note(id) ON DELETE CASCADE,
  CONSTRAINT fk_note_bookmark_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
