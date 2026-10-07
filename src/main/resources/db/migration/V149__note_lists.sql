CREATE TABLE note_list (
    id         BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    title      VARCHAR(50) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    KEY idx_note_list_user (user_id, id),
    CONSTRAINT fk_note_list_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE note_list_member (
    list_id    BIGINT      NOT NULL,
    member_id  BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (list_id, member_id),
    KEY idx_note_list_member_member (member_id),
    CONSTRAINT fk_note_list_member_list FOREIGN KEY (list_id) REFERENCES note_list(id) ON DELETE CASCADE,
    CONSTRAINT fk_note_list_member_user FOREIGN KEY (member_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
