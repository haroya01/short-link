-- Per-user feed preferences: which blog-home tab opens by default (recent/trending/following/series).
-- One row per user (upserted), so the blog home can honor the reader's chosen landing tab across
-- devices. Interest topics already live in user_tag_pref — this is only the default-tab choice.
CREATE TABLE user_feed_pref (
    id           BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT      NOT NULL,
    default_tab  VARCHAR(16) NOT NULL,
    created_at   DATETIME(6) NOT NULL,
    UNIQUE KEY uk_user_feed_pref_user (user_id),
    CONSTRAINT fk_user_feed_pref_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
