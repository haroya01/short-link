ALTER TABLE link_destination
  ADD COLUMN country_code CHAR(2) NULL,
  ADD KEY idx_link_destination_country (link_id, country_code);
