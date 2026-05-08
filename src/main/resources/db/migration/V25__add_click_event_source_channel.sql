ALTER TABLE click_event
  ADD COLUMN source_channel VARCHAR(40) NULL,
  ADD KEY idx_click_event_source_channel (link_id, source_channel);
