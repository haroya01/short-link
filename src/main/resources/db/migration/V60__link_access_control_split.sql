CREATE TABLE link_access_control (
  link_id              BIGINT      NOT NULL PRIMARY KEY,
  password_hash        VARCHAR(60),
  max_views            INT,
  created_at           DATETIME(6) NOT NULL,
  updated_at           DATETIME(6) NOT NULL,
  CONSTRAINT fk_link_access_control_link
    FOREIGN KEY (link_id) REFERENCES link(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO link_access_control (link_id, password_hash, max_views, created_at, updated_at)
SELECT id, password_hash, max_views, COALESCE(created_at, NOW(6)), NOW(6) FROM link;
