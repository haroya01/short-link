CREATE TABLE federation_actor (
  user_id            BIGINT       NOT NULL PRIMARY KEY,
  public_id          VARCHAR(32)  NOT NULL,
  public_key_pem     TEXT         NOT NULL,
  private_key_cipher TEXT         NOT NULL,
  created_at         DATETIME(6)  NOT NULL,
  updated_at         DATETIME(6)  NOT NULL,
  CONSTRAINT uk_federation_actor_public_id UNIQUE (public_id),
  CONSTRAINT fk_federation_actor_user
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
