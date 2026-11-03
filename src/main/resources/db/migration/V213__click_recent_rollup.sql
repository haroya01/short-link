-- Days between rolled_until and aggregated_until are counted twice over: their raw clicks stay in
-- click_event for the visitor reads, and their sums sit in the two tables below for the link report.
-- A report read adds click_event from aggregated_until, these tables, and the V204 rollups below
-- rolled_until; reads by visitor_hash keep reading click_event from rolled_until. Everything else
-- reads as before. rolled_until <= aggregated_until always; rolling a day into the V204 rollups
-- deletes that day's rows here in the same transaction. Lowering aggregated_until must delete the
-- rows from the new value on in the same transaction.
ALTER TABLE click_rollup_state ADD COLUMN aggregated_until TIMESTAMP NULL, ALGORITHM = INSTANT;

UPDATE click_rollup_state SET aggregated_until = rolled_until WHERE id = 1;

-- Clicks per link per 15-minute UTC bucket, split by whole days since the link was created like
-- click_hourly. Every offset in use is a multiple of 15 minutes, so a report in any zone reads its
-- local hours and days from these buckets exactly.
CREATE TABLE click_recent_quarter (
  link_id        BIGINT    NOT NULL,
  quarter_start  TIMESTAMP NOT NULL,
  age_day        INT       NOT NULL,
  humans         INT       NOT NULL,
  bots           INT       NOT NULL,
  first_human_at TIMESTAMP NULL,
  last_human_at  TIMESTAMP NULL,
  PRIMARY KEY (link_id, quarter_start, age_day),
  KEY idx_click_recent_quarter_start (quarter_start)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- The same rows as click_daily_dim for the days between rolled_until and aggregated_until.
CREATE TABLE click_recent_dim (
  link_id     BIGINT        NOT NULL,
  dim         TINYINT       NOT NULL,
  day_start   TIMESTAMP     NOT NULL,
  is_bot      BOOLEAN       NOT NULL,
  value_key   BINARY(16)    NOT NULL,
  value       VARCHAR(2048) NULL,
  num         BIGINT        NULL,
  region_code VARCHAR(6)    NULL,
  clicks      INT           NOT NULL,
  first_at    TIMESTAMP     NOT NULL,
  PRIMARY KEY (link_id, dim, day_start, is_bot, value_key),
  KEY idx_click_recent_dim_day (day_start)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
