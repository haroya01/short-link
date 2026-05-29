-- Post likes (공감). post_like is the source of truth for "who liked" (one row per user/post);
-- posts.like_count is a denormalized counter kept in sync by the like/unlike use cases so feeds
-- and lists can show the count without an aggregate per row.

ALTER TABLE posts ADD COLUMN like_count BIGINT NOT NULL DEFAULT 0;

CREATE TABLE post_like (
    id          BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    post_id     BIGINT      NOT NULL,
    user_id     BIGINT      NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    UNIQUE KEY uk_post_like_post_user (post_id, user_id),
    KEY idx_post_like_user (user_id),
    CONSTRAINT fk_post_like_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
