CREATE TABLE post_note_quote (
  post_id BIGINT NOT NULL,
  note_id BIGINT NOT NULL,
  PRIMARY KEY (post_id, note_id),
  KEY idx_post_note_quote_note (note_id, post_id),
  CONSTRAINT fk_post_note_quote_post FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
