-- A refill of note link cards that a release asks for: the row is inserted pending and the app runs
-- it by itself once after booting. Starting it sets started_at in the same transaction that sends
-- short links whose Open Graph fetch gave up (ERROR) back to RETRYABLE with no attempts, so that
-- reset happens once even across restarts. It then walks notes from the newest id down, below
-- cursor_id once set, refreshing each one that has an address but no card, and counts filled and
-- missed as it goes. finished_at closes the row for good; no restart or redeploy runs it again.
-- Nothing here is read by the release before this one, which keeps working on this schema.
CREATE TABLE note_link_preview_refill (
  name           VARCHAR(64) NOT NULL PRIMARY KEY,
  targets        INT         NULL,
  links_reopened INT         NULL,
  cursor_id      BIGINT      NULL,
  filled         INT         NOT NULL DEFAULT 0,
  missed         INT         NOT NULL DEFAULT 0,
  started_at     DATETIME(6) NULL,
  finished_at    DATETIME(6) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO note_link_preview_refill (name) VALUES ('cross-host-redirects');
