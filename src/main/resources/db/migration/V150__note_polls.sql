ALTER TABLE note
  ADD COLUMN poll_options    VARCHAR(255) NULL,
  ADD COLUMN poll_expires_at DATETIME(6)  NULL,
  ADD COLUMN poll_multiple   BOOLEAN      NOT NULL DEFAULT FALSE,
  ADD COLUMN poll_closed_at  DATETIME(6)  NULL,
  ADD INDEX idx_note_poll_due (poll_closed_at, poll_expires_at);

CREATE TABLE note_poll_vote (
  note_id    BIGINT           NOT NULL,
  user_id    BIGINT           NOT NULL,
  choices    TINYINT UNSIGNED NOT NULL,
  created_at DATETIME(6)      NOT NULL,
  PRIMARY KEY (note_id, user_id),
  INDEX idx_note_poll_vote_user (user_id),
  CONSTRAINT fk_note_poll_vote_note FOREIGN KEY (note_id) REFERENCES note(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE note_poll_remote_vote (
  note_id         BIGINT           NOT NULL,
  remote_actor_id BIGINT           NOT NULL,
  choices         TINYINT UNSIGNED NOT NULL,
  created_at      DATETIME(6)      NOT NULL,
  PRIMARY KEY (note_id, remote_actor_id),
  CONSTRAINT fk_note_poll_remote_vote_note
    FOREIGN KEY (note_id) REFERENCES note(id) ON DELETE CASCADE,
  CONSTRAINT fk_note_poll_remote_vote_actor
    FOREIGN KEY (remote_actor_id) REFERENCES federation_remote_actor(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
