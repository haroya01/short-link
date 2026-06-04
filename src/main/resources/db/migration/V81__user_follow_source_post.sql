-- Attribute each follow to the post the reader was on when they followed (nullable — a follow made
-- straight from the author profile has none). Powers the per-post "이 글로 늘어난 팔로우" analytics metric.
-- Net semantics: an unfollow deletes the row, so the attributed count reflects follows still in effect.

ALTER TABLE user_follow ADD COLUMN source_post_id BIGINT NULL;
CREATE INDEX idx_user_follow_source_post ON user_follow (source_post_id);
