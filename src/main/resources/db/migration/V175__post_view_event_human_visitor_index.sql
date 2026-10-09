CREATE INDEX idx_post_view_event_post_human ON post_view_event (post_id, is_bot, viewed_at, visitor_hash);
DROP INDEX idx_post_view_event_post_bot ON post_view_event;
