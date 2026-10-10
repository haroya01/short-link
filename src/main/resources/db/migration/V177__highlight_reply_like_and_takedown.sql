-- An admin take-down hides the reply instead of deleting it, as with comment.deleted_at.
ALTER TABLE highlight_reply ADD COLUMN deleted_at DATETIME(6) NULL;

-- The reply FK cascades like comment_like's; the user FK does not, so PostUserDataEraser purges it.
CREATE TABLE highlight_reply_like (
    id                 BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    highlight_reply_id BIGINT      NOT NULL,
    user_id            BIGINT      NOT NULL,
    created_at         DATETIME(6) NOT NULL,
    UNIQUE KEY uk_highlight_reply_like_reply_user (highlight_reply_id, user_id),
    KEY idx_highlight_reply_like_user (user_id),
    CONSTRAINT fk_highlight_reply_like_reply FOREIGN KEY (highlight_reply_id) REFERENCES highlight_reply(id) ON DELETE CASCADE,
    CONSTRAINT fk_highlight_reply_like_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
