CREATE TABLE note_repost_mute (
  id            BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id       BIGINT      NOT NULL,
  muted_user_id BIGINT      NOT NULL,
  created_at    DATETIME(6) NOT NULL,
  CONSTRAINT uk_note_repost_mute UNIQUE (user_id, muted_user_id),
  CONSTRAINT fk_note_repost_mute_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_note_repost_mute_muted FOREIGN KEY (muted_user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE note_feed_preference (
  user_id      BIGINT      NOT NULL PRIMARY KEY,
  show_reposts BOOLEAN     NOT NULL,
  updated_at   DATETIME(6) NOT NULL,
  CONSTRAINT fk_note_feed_preference_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
