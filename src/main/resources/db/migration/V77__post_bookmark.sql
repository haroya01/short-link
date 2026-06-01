-- Post bookmarks (reading list). One row per (user, post) — a user bookmarks a post at most once.
-- No denormalized counter on posts: bookmarks are a private reading list, not a public metric.
CREATE TABLE post_bookmark (
    id          BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    post_id     BIGINT      NOT NULL,
    user_id     BIGINT      NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    UNIQUE KEY uk_post_bookmark_post_user (post_id, user_id),
    KEY idx_post_bookmark_user_created (user_id, created_at),
    CONSTRAINT fk_post_bookmark_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
