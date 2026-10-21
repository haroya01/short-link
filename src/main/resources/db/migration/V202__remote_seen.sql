-- A post from another server a reader had on screen. viewer_kind 1 is a member, whose viewer_key
-- is 8 zero bytes then their id big-endian; 2 is a signed-out device, whose viewer_key is the
-- UUID it sent. uri_hash is the first 16 bytes of SHA-256 of the post's ActivityPub id. seen_at is
-- the first sighting and a later one keeps it. Rows go after 14 days, so no foreign key.
CREATE TABLE remote_seen (
  viewer_kind TINYINT     NOT NULL,
  viewer_key  BINARY(16)  NOT NULL,
  uri_hash    BINARY(16)  NOT NULL,
  seen_at     DATETIME(6) NOT NULL,
  PRIMARY KEY (viewer_kind, viewer_key, uri_hash),
  KEY idx_remote_seen_seen_at (seen_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
