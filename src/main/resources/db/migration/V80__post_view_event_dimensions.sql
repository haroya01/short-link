-- Enrich the per-post view log with the same visitor dimensions profile_visit_event already carries,
-- so the per-post / per-series reader analytics ("글마다 누가·어디서 봤는지") can break reads down by
-- country / device / browser / referrer / source / UTM / hour — at short-link-stats depth. All columns
-- are nullable: existing rows (logged before this migration) simply have no dimensions, and the view
-- write path enriches new rows going forward. Reuses the same classifier services as ClickRecorder /
-- ProfileVisitRecorder so the enrichment quality matches.
ALTER TABLE post_view_event
    ADD COLUMN referrer       TEXT         NULL,
    ADD COLUMN referrer_host  VARCHAR(255) NULL,
    ADD COLUMN user_agent     TEXT         NULL,
    ADD COLUMN client_ip      VARCHAR(45)  NULL,
    ADD COLUMN utm_source     VARCHAR(255) NULL,
    ADD COLUMN utm_medium     VARCHAR(255) NULL,
    ADD COLUMN utm_campaign   VARCHAR(255) NULL,
    ADD COLUMN utm_term       VARCHAR(255) NULL,
    ADD COLUMN utm_content    VARCHAR(255) NULL,
    ADD COLUMN device_class   VARCHAR(32)  NULL,
    ADD COLUMN os_name        VARCHAR(64)  NULL,
    ADD COLUMN browser_name   VARCHAR(64)  NULL,
    ADD COLUMN is_bot         BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN bot_name       VARCHAR(64)  NULL,
    ADD COLUMN country_code   VARCHAR(2)   NULL,
    ADD COLUMN region_name    VARCHAR(128) NULL,
    ADD COLUMN city_name      VARCHAR(128) NULL,
    ADD COLUMN language       VARCHAR(8)   NULL,
    ADD COLUMN visitor_hash   VARCHAR(64)  NULL,
    ADD COLUMN source_channel VARCHAR(64)  NULL;

-- Breakdown aggregation filters human reads (is_bot = false) over a post-id set; this index serves it.
CREATE INDEX idx_post_view_event_post_bot ON post_view_event (post_id, is_bot);
