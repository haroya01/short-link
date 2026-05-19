CREATE TABLE request_metrics (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  occurred_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  route VARCHAR(255) NOT NULL,
  method VARCHAR(8) NOT NULL,
  status SMALLINT NOT NULL,
  outcome VARCHAR(32) NOT NULL,
  latency_ms INT UNSIGNED NOT NULL,
  short_code VARCHAR(64) NULL,
  user_id BIGINT NULL,
  trace_id VARCHAR(64) NULL,
  PRIMARY KEY (id),
  KEY idx_occurred (occurred_at),
  KEY idx_route_occurred (route, occurred_at),
  KEY idx_short_occurred (short_code, occurred_at),
  KEY idx_outcome_occurred (outcome, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
