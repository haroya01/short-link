CREATE TABLE link_visit_option (
  link_id          BIGINT      NOT NULL PRIMARY KEY,
  open_in_browser  BOOLEAN     NOT NULL DEFAULT FALSE,
  created_at       DATETIME(6) NOT NULL,
  updated_at       DATETIME(6) NOT NULL,
  CONSTRAINT fk_link_visit_option_link
    FOREIGN KEY (link_id) REFERENCES link(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
