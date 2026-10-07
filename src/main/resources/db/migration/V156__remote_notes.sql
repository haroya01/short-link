ALTER TABLE note
  MODIFY COLUMN user_id BIGINT NULL,
  MODIFY COLUMN body VARCHAR(5000) NOT NULL,
  ADD COLUMN remote_actor_id BIGINT NULL AFTER user_id,
  ADD COLUMN uri VARCHAR(512) NULL,
  ADD COLUMN remote_url VARCHAR(512) NULL,
  ADD CONSTRAINT uk_note_uri UNIQUE (uri),
  ADD KEY idx_note_remote_actor (remote_actor_id, id),
  ADD CONSTRAINT fk_note_remote_actor
    FOREIGN KEY (remote_actor_id) REFERENCES federation_remote_actor(id) ON DELETE CASCADE;
