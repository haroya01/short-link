CREATE TABLE note_link_preview (
  note_id     BIGINT        NOT NULL PRIMARY KEY,
  url         VARCHAR(2048) NOT NULL,
  title       VARCHAR(300)  NULL,
  description VARCHAR(800)  NULL,
  image_url   VARCHAR(1024) NULL,
  fetched_at  DATETIME(6)   NOT NULL,
  CONSTRAINT fk_note_link_preview_note FOREIGN KEY (note_id) REFERENCES note(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
