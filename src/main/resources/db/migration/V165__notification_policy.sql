CREATE TABLE notification_policy (
  user_id              BIGINT      NOT NULL PRIMARY KEY,
  for_not_following    VARCHAR(8)  NOT NULL,
  for_not_followers    VARCHAR(8)  NOT NULL,
  for_new_accounts     VARCHAR(8)  NOT NULL,
  for_private_mentions VARCHAR(8)  NOT NULL,
  updated_at           DATETIME(6) NOT NULL,
  CONSTRAINT fk_notification_policy_user
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE notification_permission (
  id                BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
  recipient_user_id BIGINT      NOT NULL,
  actor_user_id     BIGINT      NULL,
  actor_remote_id   BIGINT      NULL,
  created_at        DATETIME(6) NOT NULL,
  KEY idx_notification_permission (recipient_user_id, actor_user_id, actor_remote_id),
  CONSTRAINT fk_notification_permission_recipient
    FOREIGN KEY (recipient_user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_notification_permission_actor
    FOREIGN KEY (actor_user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_notification_permission_remote
    FOREIGN KEY (actor_remote_id) REFERENCES federation_remote_actor(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE notification ADD COLUMN filtered BOOLEAN NOT NULL DEFAULT FALSE;
