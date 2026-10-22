-- The counts a kept note from elsewhere has on its own server, read when it was kept, whenever a
-- trending list carries it, and again by asking ask_host for ask_id at next_check_at. A NULL
-- next_check_at asks no more; gone_at is set once the server answers without it.
CREATE TABLE remote_note_reach (
  note_id       BIGINT       NOT NULL PRIMARY KEY,
  ask_host      VARCHAR(253) NULL,
  ask_id        VARCHAR(64)  NULL,
  likes         INT          NOT NULL DEFAULT 0,
  reposts       INT          NOT NULL DEFAULT 0,
  replies       INT          NOT NULL DEFAULT 0,
  counted_at    DATETIME(6)  NULL,
  trending_at   DATETIME(6)  NULL,
  next_check_at DATETIME(6)  NULL,
  checks        TINYINT      NOT NULL DEFAULT 0,
  failures      TINYINT      NOT NULL DEFAULT 0,
  gone_at       DATETIME(6)  NULL,
  KEY idx_remote_note_reach_due (next_check_at),
  CONSTRAINT fk_remote_note_reach_note FOREIGN KEY (note_id) REFERENCES note(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- How long a reader had a kept note from elsewhere on screen, the longest of what they sent. The
-- viewer columns are remote_seen's. Rows go after 30 days.
CREATE TABLE remote_dwell (
  viewer_kind TINYINT     NOT NULL,
  viewer_key  BINARY(16)  NOT NULL,
  note_id     BIGINT      NOT NULL,
  dwell_ms    INT         NOT NULL,
  dwelt_at    DATETIME(6) NOT NULL,
  PRIMARY KEY (viewer_kind, viewer_key, note_id),
  KEY idx_remote_dwell_note (note_id),
  KEY idx_remote_dwell_at (dwelt_at),
  CONSTRAINT fk_remote_dwell_note FOREIGN KEY (note_id) REFERENCES note(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- A note kept because another server listed it as trending is pulled_from that server and trended.
-- It counts toward its own day's cap, not the pulls'.
ALTER TABLE federation_relayed_note
  ADD COLUMN trended BOOLEAN NOT NULL DEFAULT FALSE;
