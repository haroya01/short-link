ALTER TABLE users ADD COLUMN locked BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE follow_request (
  id           BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
  follower_id  BIGINT      NOT NULL,
  following_id BIGINT      NOT NULL,
  created_at   DATETIME(6) NOT NULL,
  CONSTRAINT uk_follow_request UNIQUE (following_id, follower_id),
  KEY idx_follow_request_follower (follower_id),
  CONSTRAINT fk_follow_request_follower
    FOREIGN KEY (follower_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_follow_request_following
    FOREIGN KEY (following_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE federation_follower ADD COLUMN accepted_at DATETIME(6) NULL;
UPDATE federation_follower SET accepted_at = created_at;
