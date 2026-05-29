-- Follow graph (velog 식 구독). One row per (follower → following) pair. Follower counts are read
-- on demand (COUNT) rather than denormalized — they're only surfaced on the author page (a single
-- lookup), so a counter column isn't worth the write-path coupling.

CREATE TABLE user_follow (
    id           BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    follower_id  BIGINT      NOT NULL,
    following_id BIGINT      NOT NULL,
    created_at   DATETIME(6) NOT NULL,
    UNIQUE KEY uk_user_follow (follower_id, following_id),
    KEY idx_user_follow_following (following_id),
    CONSTRAINT fk_user_follow_follower FOREIGN KEY (follower_id) REFERENCES users(id),
    CONSTRAINT fk_user_follow_following FOREIGN KEY (following_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
