-- Attribute a click to the blog post that embedded the link ("이 글이 만든 클릭"). Nullable: only
-- clicks arriving with ?post= from a post body carry it. Indexed for the per-post / per-author
-- analytics aggregation; no FK to keep the high-volume click write path light.
ALTER TABLE click_event ADD COLUMN post_id BIGINT NULL;
CREATE INDEX idx_click_event_post ON click_event (post_id);
