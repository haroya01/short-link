ALTER TABLE link_visit_option
  ADD COLUMN splash_enabled BOOLEAN      NOT NULL DEFAULT FALSE,
  ADD COLUMN splash_message VARCHAR(280) NULL,
  ADD COLUMN splash_seconds INT          NOT NULL DEFAULT 3,
  ADD COLUMN splash_cta_id  BIGINT       NULL,
  ADD CONSTRAINT fk_link_visit_option_splash_cta
    FOREIGN KEY (splash_cta_id) REFERENCES cta(id) ON DELETE SET NULL;
