CREATE TABLE note_remote_reaction (
  id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  note_id         BIGINT       NOT NULL,
  remote_actor_id BIGINT       NOT NULL,
  kind            VARCHAR(8)   NOT NULL,
  activity_id     VARCHAR(512) NOT NULL,
  created_at      DATETIME(6)  NOT NULL,
  CONSTRAINT uk_note_remote_reaction UNIQUE (note_id, remote_actor_id, kind),
  CONSTRAINT fk_note_remote_reaction_note
    FOREIGN KEY (note_id) REFERENCES note(id) ON DELETE CASCADE,
  CONSTRAINT fk_note_remote_reaction_actor
    FOREIGN KEY (remote_actor_id) REFERENCES federation_remote_actor(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
