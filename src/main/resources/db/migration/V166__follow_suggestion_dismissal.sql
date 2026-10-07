CREATE TABLE follow_suggestion_dismissal (
  user_id      BIGINT      NOT NULL,
  dismissed_id BIGINT      NOT NULL,
  created_at   DATETIME(6) NOT NULL,
  PRIMARY KEY (user_id, dismissed_id),
  CONSTRAINT fk_follow_suggestion_dismissal_user
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_follow_suggestion_dismissal_dismissed
    FOREIGN KEY (dismissed_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
