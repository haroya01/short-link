ALTER TABLE link_destination
  ADD COLUMN device_class VARCHAR(16) NULL,
  ADD COLUMN os VARCHAR(16) NULL;

ALTER TABLE link
  ADD COLUMN blocked_countries VARCHAR(255) NULL;
