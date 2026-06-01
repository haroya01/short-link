-- Per-user tag preferences: FOLLOW (surface in a personal strip) or HIDE (filter from feeds).
-- One row per (user, tag) — the two intents are mutually exclusive, so following a hidden tag just
-- flips this row's kind. Replaces the device-local localStorage version with account sync.
CREATE TABLE user_tag_pref (
    id          BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT      NOT NULL,
    tag         VARCHAR(40) NOT NULL,
    kind        VARCHAR(8)  NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    UNIQUE KEY uk_user_tag_pref_user_tag (user_id, tag),
    KEY idx_user_tag_pref_user (user_id),
    CONSTRAINT fk_user_tag_pref_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
