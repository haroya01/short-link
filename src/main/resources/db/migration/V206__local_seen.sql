-- A note or blog post from this server a reader had on screen in the home feed. The viewer columns
-- are remote_seen's. item_kind 1 is a note and 2 a post, item_id its id. seen_at is the first
-- sighting as UTC wall time and a later one keeps it. Rows go after 14 days, so no foreign key.
CREATE TABLE local_seen (
  viewer_kind TINYINT     NOT NULL,
  viewer_key  BINARY(16)  NOT NULL,
  item_kind   TINYINT     NOT NULL,
  item_id     BIGINT      NOT NULL,
  seen_at     DATETIME(6) NOT NULL,
  PRIMARY KEY (viewer_kind, viewer_key, item_kind, item_id),
  KEY idx_local_seen_seen_at (seen_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
