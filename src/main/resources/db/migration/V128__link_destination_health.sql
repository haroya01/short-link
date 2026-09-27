CREATE TABLE link_destination_health (
  link_id       BIGINT        NOT NULL PRIMARY KEY,
  checked_url   VARCHAR(2048) NOT NULL,
  failure       VARCHAR(16) NULL,
  http_status   INT         NULL,
  failures      INT         NOT NULL DEFAULT 0,
  checked_at    DATETIME(6) NOT NULL,
  broken_since  DATETIME(6) NULL,
  created_at    DATETIME(6) NOT NULL,
  updated_at    DATETIME(6) NOT NULL,
  CONSTRAINT fk_link_destination_health_link
    FOREIGN KEY (link_id) REFERENCES link(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_link_destination_health_checked ON link_destination_health (checked_at);
