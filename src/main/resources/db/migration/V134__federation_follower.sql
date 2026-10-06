CREATE TABLE federation_follower (
  id                 BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id            BIGINT        NOT NULL,
  remote_actor_id    BIGINT        NOT NULL,
  follow_activity_id VARCHAR(512)  NOT NULL,
  created_at         DATETIME(6)   NOT NULL,
  updated_at         DATETIME(6)   NOT NULL,
  CONSTRAINT uk_federation_follower UNIQUE (user_id, remote_actor_id),
  CONSTRAINT fk_federation_follower_user
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_federation_follower_actor
    FOREIGN KEY (remote_actor_id) REFERENCES federation_remote_actor(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
