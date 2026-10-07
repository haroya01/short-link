CREATE TABLE user_mute (
    id                 BIGINT      NOT NULL AUTO_INCREMENT,
    user_id            BIGINT      NOT NULL,
    muted_user_id      BIGINT      NOT NULL,
    hide_notifications BOOLEAN     NOT NULL,
    expires_at         DATETIME(6) NULL,
    created_at         DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_user_mute UNIQUE (user_id, muted_user_id)
);

CREATE INDEX idx_user_mute_muted ON user_mute (muted_user_id);
