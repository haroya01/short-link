ALTER TABLE note ADD COLUMN visibility VARCHAR(16) NOT NULL DEFAULT 'PUBLIC';

-- Members a followers-only or direct note mentions: they see it even when they do not follow.
CREATE TABLE note_recipient (
    note_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    PRIMARY KEY (note_id, user_id),
    KEY idx_note_recipient_user (user_id, note_id),
    CONSTRAINT fk_note_recipient_note FOREIGN KEY (note_id) REFERENCES note(id) ON DELETE CASCADE,
    CONSTRAINT fk_note_recipient_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
