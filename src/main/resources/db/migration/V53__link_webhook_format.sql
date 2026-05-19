ALTER TABLE link_webhook
  ADD COLUMN format VARCHAR(16) NOT NULL DEFAULT 'GENERIC';

-- Backfill: discord/slack URLs registered before the adapter shipped need to flip to the matching
-- format so the next click stops bouncing off the receiver's payload contract.
UPDATE link_webhook
SET format = 'DISCORD'
WHERE url LIKE 'https://discord.com/api/webhooks/%'
   OR url LIKE 'https://discordapp.com/api/webhooks/%';

UPDATE link_webhook
SET format = 'SLACK'
WHERE url LIKE 'https://hooks.slack.com/services/%';
