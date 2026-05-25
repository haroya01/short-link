CREATE TABLE link_expiration_policy (
  link_id              BIGINT       NOT NULL PRIMARY KEY,
  blocked_countries    VARCHAR(255),
  expired_message      VARCHAR(500),
  expired_redirect_url VARCHAR(2048),
  created_at           DATETIME(6)  NOT NULL,
  updated_at           DATETIME(6)  NOT NULL,
  CONSTRAINT fk_link_expiration_policy_link
    FOREIGN KEY (link_id) REFERENCES link(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO link_expiration_policy (
  link_id, blocked_countries, expired_message, expired_redirect_url, created_at, updated_at
)
SELECT id, blocked_countries, expired_message, expired_redirect_url, COALESCE(created_at, NOW(6)), NOW(6)
FROM link;
