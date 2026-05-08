CREATE TABLE link_destination (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  link_id BIGINT NOT NULL,
  url VARCHAR(2048) NOT NULL,
  weight INT NOT NULL DEFAULT 1,
  label VARCHAR(40) NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME(3) NOT NULL,
  KEY idx_link_destination_link (link_id, enabled),
  CONSTRAINT fk_link_destination_link FOREIGN KEY (link_id) REFERENCES link(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE click_event ADD COLUMN destination_id BIGINT NULL;
ALTER TABLE click_event ADD KEY idx_click_event_destination (destination_id);
