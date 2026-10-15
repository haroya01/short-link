-- How many people have read a post: distinct human visitors of its page (bots, the signed-in
-- author and visits without a visitor hash left out). Only the row-locked view path moves it, one
-- reader at a time, so each threshold is crossed in exactly one transaction.
ALTER TABLE posts
  ADD COLUMN human_reader_count INT NOT NULL DEFAULT 0;

-- Thresholds already notified, one row per post and threshold (1 = first reader), so a notice goes
-- out once even if a count passes the same threshold again. Rows go with the post.
CREATE TABLE post_read_milestone (
  post_id    BIGINT      NOT NULL,
  milestone  INT         NOT NULL,
  reached_at DATETIME(6) NOT NULL,
  PRIMARY KEY (post_id, milestone),
  CONSTRAINT fk_post_read_milestone_post FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Whether a visitor has read the post before is one lookup on this key.
ALTER TABLE post_view_event
  ADD KEY idx_post_view_event_post_visitor (post_id, visitor_hash);

-- Existing posts start from the readers they already have, and the thresholds they already passed
-- count as notified, so nothing is announced for the past.
UPDATE posts p
SET p.human_reader_count = (
  SELECT COUNT(DISTINCT e.visitor_hash) FROM post_view_event e
  WHERE e.post_id = p.id AND e.is_bot = FALSE AND e.visitor_hash IS NOT NULL);

INSERT INTO post_read_milestone (post_id, milestone, reached_at)
SELECT p.id, m.milestone, NOW(6)
FROM posts p
JOIN (SELECT 1 AS milestone UNION ALL SELECT 10 UNION ALL SELECT 100 UNION ALL SELECT 1000) m
  ON p.human_reader_count >= m.milestone;
