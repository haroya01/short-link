CREATE TABLE federation_instance_actor (
  id                 TINYINT      NOT NULL PRIMARY KEY,
  public_key_pem     TEXT         NOT NULL,
  private_key_cipher TEXT         NOT NULL,
  created_at         DATETIME(6)  NOT NULL,
  updated_at         DATETIME(6)  NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE federation_remote_actor (
  id             BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
  actor_uri      VARCHAR(512)  NOT NULL,
  key_id         VARCHAR(600)  NOT NULL,
  public_key_pem TEXT          NOT NULL,
  inbox          VARCHAR(512)  NOT NULL,
  shared_inbox   VARCHAR(512)  NULL,
  username       VARCHAR(255)  NULL,
  domain         VARCHAR(255)  NOT NULL,
  profile_url    VARCHAR(512)  NULL,
  display_name   VARCHAR(255)  NULL,
  avatar_url     VARCHAR(512)  NULL,
  fetched_at     DATETIME(6)   NOT NULL,
  created_at     DATETIME(6)   NOT NULL,
  updated_at     DATETIME(6)   NOT NULL,
  CONSTRAINT uk_federation_remote_actor_uri UNIQUE (actor_uri)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_federation_remote_actor_key ON federation_remote_actor (key_id);

CREATE TABLE federation_delivery (
  id              BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
  dedupe_key      CHAR(64)      NOT NULL,
  inbox           VARCHAR(512)  NOT NULL,
  inbox_host      VARCHAR(255)  NOT NULL,
  signer_user_id  BIGINT        NULL,
  activity_id     VARCHAR(512)  NOT NULL,
  body            MEDIUMTEXT    NOT NULL,
  status          VARCHAR(16)   NOT NULL,
  attempts        INT           NOT NULL DEFAULT 0,
  next_attempt_at DATETIME(6)   NOT NULL,
  last_status     INT           NULL,
  last_error      VARCHAR(255)  NULL,
  created_at      DATETIME(6)   NOT NULL,
  updated_at      DATETIME(6)   NOT NULL,
  CONSTRAINT uk_federation_delivery_dedupe UNIQUE (dedupe_key),
  CONSTRAINT fk_federation_delivery_signer
    FOREIGN KEY (signer_user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_federation_delivery_due ON federation_delivery (status, next_attempt_at);
