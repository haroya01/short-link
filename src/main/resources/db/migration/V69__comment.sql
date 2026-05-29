-- Post comments — one level of threading (parent_id → top-level comment). Read by anyone,
-- created by authenticated users. No FK to posts (soft reference) so a future post hard-delete
-- path can clean up comments explicitly rather than via cascade surprises.

CREATE TABLE comment (
    id          BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    post_id     BIGINT        NOT NULL,
    user_id     BIGINT        NOT NULL,
    parent_id   BIGINT        NULL,
    body        VARCHAR(2000) NOT NULL,
    created_at  DATETIME(6)   NOT NULL,
    updated_at  DATETIME(6)   NOT NULL,
    KEY idx_comment_post_created (post_id, created_at),
    KEY idx_comment_parent (parent_id),
    CONSTRAINT fk_comment_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
