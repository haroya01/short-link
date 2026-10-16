-- An instance whose public profile directory (Mastodon's /api/v1/directory?local=true) is read so
-- its people can be found by name here. Only accounts that agreed to be found are listed there. A
-- run reads at most cap of its most active accounts; a failed run waits until retry_after.
CREATE TABLE federation_directory_source (
  host           VARCHAR(255)  NOT NULL PRIMARY KEY,
  enabled        BOOLEAN       NOT NULL,
  cap            INT           NOT NULL,
  failures       INT           NOT NULL DEFAULT 0,
  retry_after    DATETIME(6)   NULL,
  last_synced_at DATETIME(6)   NULL,
  last_outcome   VARCHAR(255)  NULL,
  created_at     DATETIME(6)   NOT NULL,
  updated_at     DATETIME(6)   NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- When the account last appeared in a directory read here. An account seen in a directory stays
-- findable by name only while it appeared there, or its own server said discoverable, within the
-- last three days; otherwise discoverable is turned off. NULL for accounts no directory listed.
ALTER TABLE federation_remote_actor
  ADD COLUMN directory_seen_at DATETIME(6) NULL;
