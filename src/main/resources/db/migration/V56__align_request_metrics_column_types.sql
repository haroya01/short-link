-- Hibernate schema-validation enforces a strict column-type match against the JPA mappings on
-- RequestMetricEntity (Long id, int status, long latencyMs). V55 created the table with MySQL
-- native compaction types — BIGINT UNSIGNED / SMALLINT / INT UNSIGNED — and validation rejects
-- them as the wrong SQL types for the Java field widths. Align them here so startup-time
-- validation passes without weakening the JPA model (which several downstream services already
-- consume as plain long / int).
ALTER TABLE request_metrics
  MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT,
  MODIFY COLUMN status INT NOT NULL,
  MODIFY COLUMN latency_ms BIGINT NOT NULL;
