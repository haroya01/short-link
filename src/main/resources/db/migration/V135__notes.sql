ALTER TABLE note
  ADD COLUMN in_reply_to_id BIGINT      NULL AFTER body,
  ADD COLUMN quoted_post_id BIGINT      NULL AFTER in_reply_to_id,
  ADD COLUMN edited_at      DATETIME(6) NULL AFTER quoted_post_id,
  ADD KEY idx_note_reply (in_reply_to_id),
  ADD CONSTRAINT fk_note_reply FOREIGN KEY (in_reply_to_id) REFERENCES note(id) ON DELETE SET NULL,
  ADD CONSTRAINT fk_note_quoted_post FOREIGN KEY (quoted_post_id) REFERENCES posts(id) ON DELETE SET NULL;

CREATE TABLE note_media (
  id           BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
  note_id      BIGINT        NOT NULL,
  position     INT           NOT NULL,
  storage_key  VARCHAR(256)  NOT NULL,
  url          VARCHAR(512)  NOT NULL,
  content_type VARCHAR(32)   NOT NULL,
  alt_text     VARCHAR(1500) NULL,
  created_at   DATETIME(6)   NOT NULL,
  CONSTRAINT uk_note_media_position UNIQUE (note_id, position),
  CONSTRAINT fk_note_media_note FOREIGN KEY (note_id) REFERENCES note(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
