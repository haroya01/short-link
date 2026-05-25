-- Phase 1 of LinkEntity decomposition: lift OG metadata into its own 1:1 table.
-- Expand-contract rollout — this PR creates the new table and the
-- LinkOgMetadata entity; the application keeps writing to the original
-- columns on link.og_* as well (dual-write) so a rollback to the previous
-- release stays safe. Phase 2 (read switch) and Phase 3 (drop the legacy
-- columns) ship in later waves once Phase 1 has soaked.

CREATE TABLE link_og_metadata (
  link_id              BIGINT      NOT NULL PRIMARY KEY,
  og_title             VARCHAR(300),
  og_description       VARCHAR(800),
  og_image             VARCHAR(1024),
  og_fetched_at        DATETIME(6),
  og_fetch_status      VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  og_fetch_attempts    INT         NOT NULL DEFAULT 0,
  og_title_override    VARCHAR(300),
  og_description_override VARCHAR(800),
  og_image_override    VARCHAR(1024),
  created_at           DATETIME(6) NOT NULL,
  updated_at           DATETIME(6) NOT NULL,
  CONSTRAINT fk_link_og_metadata_link
    FOREIGN KEY (link_id) REFERENCES link(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Backfill: every existing link gets a row keyed on its id. Subsequent
-- writes go to both sides until Phase 2 promotes link_og_metadata to the
-- read source.
INSERT INTO link_og_metadata (
  link_id, og_title, og_description, og_image, og_fetched_at,
  og_fetch_status, og_fetch_attempts,
  og_title_override, og_description_override, og_image_override,
  created_at, updated_at
)
SELECT
  id, og_title, og_description, og_image, og_fetched_at,
  COALESCE(og_fetch_status, 'PENDING'), COALESCE(og_fetch_attempts, 0),
  og_title_override, og_description_override, og_image_override,
  COALESCE(created_at, NOW(6)), NOW(6)
FROM link;
