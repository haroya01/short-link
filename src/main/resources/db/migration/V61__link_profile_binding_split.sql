CREATE TABLE link_profile_binding (
  link_id              BIGINT      NOT NULL PRIMARY KEY,
  profile_order        INT,
  profile_highlighted  TINYINT(1)  NOT NULL DEFAULT 0,
  created_at           DATETIME(6) NOT NULL,
  updated_at           DATETIME(6) NOT NULL,
  CONSTRAINT fk_link_profile_binding_link
    FOREIGN KEY (link_id) REFERENCES link(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO link_profile_binding (link_id, profile_order, profile_highlighted, created_at, updated_at)
SELECT id, profile_order, profile_highlighted, COALESCE(created_at, NOW(6)), NOW(6) FROM link;
