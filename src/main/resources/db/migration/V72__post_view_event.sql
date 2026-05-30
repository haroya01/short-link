-- Per-view event log for published posts. Each public post view appends one row (anonymous, no
-- dedup — mirrors the view_count counter's v0 semantics). This is what lets "trending" mean recent
-- traction (views inside a rolling window) instead of all-time view_count: posts.view_count stays
-- the denormalized lifetime counter shown on cards, while this table is the time-sliced source of
-- truth the trending feed ranks on.
--
-- ON DELETE CASCADE (same as post_tag) so a hard post delete cleans its events without touching the
-- delete use case; idx on (viewed_at) serves the window filter and (post_id, viewed_at) the per-post
-- windowed COUNT join.
CREATE TABLE post_view_event (
    id         BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    post_id    BIGINT      NOT NULL,
    viewed_at  DATETIME(6) NOT NULL,
    KEY idx_post_view_event_time (viewed_at),
    KEY idx_post_view_event_post_time (post_id, viewed_at),
    CONSTRAINT fk_post_view_event_post FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
