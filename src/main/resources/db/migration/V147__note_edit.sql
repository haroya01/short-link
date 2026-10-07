CREATE TABLE note_edit (
    id               BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    note_id          BIGINT       NOT NULL,
    body             VARCHAR(500) NOT NULL,
    content_warning  VARCHAR(100) NULL,
    marked_sensitive BOOLEAN      NOT NULL DEFAULT FALSE,
    written_at       DATETIME(6)  NOT NULL,
    KEY idx_note_edit_note (note_id, id),
    CONSTRAINT fk_note_edit_note FOREIGN KEY (note_id) REFERENCES note(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
