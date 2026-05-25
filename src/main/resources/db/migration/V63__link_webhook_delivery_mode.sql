-- Webhook delivery mode + summary/spike config. Existing rows keep per-event behaviour via the
-- PER_EVENT default — the existing batch_enabled boolean keeps working as an orthogonal axis on top
-- of PER_EVENT (5s buffer of raw events). DAILY_SUMMARY / THRESHOLD_SPIKE / BOTH are new opt-ins.
ALTER TABLE link_webhook
    ADD COLUMN delivery_mode          VARCHAR(32)  NOT NULL DEFAULT 'PER_EVENT',
    ADD COLUMN summary_hour_of_day    INT          NULL,
    ADD COLUMN summary_last_sent_date DATE         NULL,
    ADD COLUMN spike_threshold        INT          NULL,
    ADD COLUMN spike_window_minutes   INT          NULL,
    ADD COLUMN spike_last_fired_at    DATETIME(6)  NULL;
