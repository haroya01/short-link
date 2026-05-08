ALTER TABLE click_event
  ADD COLUMN asn INT NULL,
  ADD COLUMN asn_org VARCHAR(200) NULL,
  ADD KEY idx_click_event_asn (link_id, asn);
