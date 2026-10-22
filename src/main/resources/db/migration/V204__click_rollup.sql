-- Clicks before rolled_until are kept only as the rollups below; clicks at or after it only in
-- click_event. A read reaching past the raw retention adds both sides and must filter click_event
-- by clicked_at >= rolled_until, since rows below it wait for the nightly delete after their day
-- was rolled up. Exactly one row, id 1. The rollup advances it one UTC day per transaction.
CREATE TABLE click_rollup_state (
  id           TINYINT   NOT NULL PRIMARY KEY,
  rolled_until TIMESTAMP NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO click_rollup_state (id, rolled_until) VALUES (1, FROM_UNIXTIME(1));

-- Clicks per link per UTC hour, split by whole days since the link was created (age_day, the same
-- TIMESTAMPDIFF the lifecycle reads use), so an hour straddling a link-age day has two rows.
-- hour_start is the hour's first instant. first/last_human_at and last_at are the exact
-- MIN/MAX(clicked_at) inside the bucket.
CREATE TABLE click_hourly (
  link_id        BIGINT    NOT NULL,
  hour_start     TIMESTAMP NOT NULL,
  age_day        INT       NOT NULL,
  humans         INT       NOT NULL,
  bots           INT       NOT NULL,
  first_human_at TIMESTAMP NULL,
  last_human_at  TIMESTAMP NULL,
  last_at        TIMESTAMP NOT NULL,
  PRIMARY KEY (link_id, hour_start, age_day),
  KEY idx_click_hourly_hour (hour_start)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Clicks per link per UTC day per value of one dimension (ClickRollup codes). value holds a
-- text dimension cut at 2048 characters, num a numeric one; asn keeps its number in num and its
-- organisation in value. value_key is MD5 of the whole value and num, unique within (link_id, dim,
-- day_start, is_bot). value uses the click_event collation so a read grouping both sides merges
-- values the way click_event alone did. first_at is MIN(clicked_at) of the group.
CREATE TABLE click_daily_dim (
  link_id   BIGINT        NOT NULL,
  dim       TINYINT       NOT NULL,
  day_start TIMESTAMP     NOT NULL,
  is_bot    BOOLEAN       NOT NULL,
  value_key BINARY(16)    NOT NULL,
  value     VARCHAR(2048) NULL,
  num       BIGINT        NULL,
  clicks    INT           NOT NULL,
  first_at  TIMESTAMP     NOT NULL,
  PRIMARY KEY (link_id, dim, day_start, is_bot, value_key),
  KEY idx_click_daily_dim_num (dim, num, day_start)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- A DataSketches HLL (lgK 12, HLL_4, compact) of one UTC day's human visitors of a link, fed with
-- the first 64 bits of visitor_hash. No visitor_hash outlives click_event.
CREATE TABLE click_daily_visitors (
  link_id   BIGINT    NOT NULL,
  day_start TIMESTAMP NOT NULL,
  sketch    BLOB      NOT NULL,
  PRIMARY KEY (link_id, day_start)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- The rollup reads one day of click_event at a time and the delete walks clicked_at upward.
ALTER TABLE click_event ADD KEY idx_click_event_clicked_at (clicked_at);
