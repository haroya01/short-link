ALTER TABLE notification
  ADD COLUMN group_key VARCHAR(64) NULL AFTER type,
  ADD COLUMN actor_remote_id BIGINT NULL AFTER actor_user_id,
  ADD KEY idx_notification_group (recipient_user_id, group_key, id),
  ADD CONSTRAINT fk_notification_actor_remote
    FOREIGN KEY (actor_remote_id) REFERENCES federation_remote_actor(id) ON DELETE CASCADE;
