ALTER TABLE note
  ADD COLUMN quoted_note_id BIGINT NULL AFTER quoted_post_id,
  ADD CONSTRAINT fk_note_quoted_note FOREIGN KEY (quoted_note_id) REFERENCES note(id) ON DELETE SET NULL;

CREATE TABLE note_repost (
  id         BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
  note_id    BIGINT      NOT NULL,
  user_id    BIGINT      NOT NULL,
  created_at DATETIME(6) NOT NULL,
  CONSTRAINT uk_note_repost UNIQUE (note_id, user_id),
  KEY idx_note_repost_user (user_id, id),
  CONSTRAINT fk_note_repost_note FOREIGN KEY (note_id) REFERENCES note(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
