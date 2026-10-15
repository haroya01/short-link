-- A member's block or mute of one account on another server, as on Mastodon. A block keeps the id
-- of the Block activity sent to that server, so unblocking can Undo it; the id is NULL when the
-- member had federation off and nothing was sent. Both rows go with the member or the account by
-- ON DELETE CASCADE.
CREATE TABLE federation_remote_block (
  id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id         BIGINT       NOT NULL,
  remote_actor_id BIGINT       NOT NULL,
  activity_id     VARCHAR(512) NULL,
  created_at      DATETIME(6)  NOT NULL,
  CONSTRAINT uk_federation_remote_block UNIQUE (user_id, remote_actor_id),
  KEY idx_federation_remote_block_actor (remote_actor_id),
  CONSTRAINT fk_federation_remote_block_user
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_federation_remote_block_actor
    FOREIGN KEY (remote_actor_id) REFERENCES federation_remote_actor(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE federation_remote_mute (
  id                 BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id            BIGINT      NOT NULL,
  remote_actor_id    BIGINT      NOT NULL,
  hide_notifications BOOLEAN     NOT NULL,
  expires_at         DATETIME(6) NULL,
  created_at         DATETIME(6) NOT NULL,
  CONSTRAINT uk_federation_remote_mute UNIQUE (user_id, remote_actor_id),
  KEY idx_federation_remote_mute_actor (remote_actor_id),
  CONSTRAINT fk_federation_remote_mute_user
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_federation_remote_mute_actor
    FOREIGN KEY (remote_actor_id) REFERENCES federation_remote_actor(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
