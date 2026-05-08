ALTER TABLE link_webhook
  ADD COLUMN include_bots BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN sample_rate INT NOT NULL DEFAULT 100,
  ADD COLUMN batch_enabled BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN daily_quota INT NULL,
  ADD COLUMN consecutive_failures INT NOT NULL DEFAULT 0,
  ADD COLUMN auto_disabled_reason VARCHAR(200) NULL,
  ADD COLUMN referrer_host_filter VARCHAR(255) NULL,
  ADD COLUMN utm_source_filter VARCHAR(100) NULL;
