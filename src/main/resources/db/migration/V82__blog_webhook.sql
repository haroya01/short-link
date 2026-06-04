-- Blog notification webhooks: one row per author endpoint. Fires when a reader likes/comments/
-- follows/subscribes across any of the author's posts (scoped to the user, not a single post).
-- `events` is a CSV of interaction names the hook subscribes to; delivery state mirrors link_webhook
-- so the hook self-disables after repeated failures.

CREATE TABLE blog_webhook (
    id                   BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id              BIGINT       NOT NULL,
    url                  VARCHAR(2048) NOT NULL,
    secret               VARCHAR(64)  NOT NULL,
    name                 VARCHAR(100) NULL,
    format               VARCHAR(16)  NOT NULL DEFAULT 'GENERIC',
    events               VARCHAR(255) NOT NULL,
    enabled              BOOLEAN      NOT NULL DEFAULT TRUE,
    last_called_at       DATETIME(6)  NULL,
    last_status_code     INT          NULL,
    last_error           VARCHAR(500) NULL,
    consecutive_failures INT          NOT NULL DEFAULT 0,
    auto_disabled_reason VARCHAR(200) NULL,
    created_at           DATETIME(6)  NOT NULL,
    KEY idx_blog_webhook_user (user_id, enabled),
    CONSTRAINT fk_blog_webhook_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
