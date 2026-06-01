-- Series subscriptions ("구독"). One row per (user, series) — a user subscribes to a series at most
-- once. Drives the following feed: new episodes of subscribed series surface there alongside the
-- posts of followed authors. No denormalized counter — subscriber count is derived when needed.
CREATE TABLE series_subscription (
    id          BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT      NOT NULL,
    series_id   BIGINT      NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    UNIQUE KEY uk_series_subscription_user_series (user_id, series_id),
    KEY idx_series_subscription_user (user_id),
    KEY idx_series_subscription_series (series_id),
    CONSTRAINT fk_series_subscription_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
