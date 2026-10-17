-- One row per original under note-images/ or post-images/ whose resized copies (<name>.w480.<ext>,
-- <name>.w1080.<ext>) were written (MADE) or can never be written (FAILED: gone or undecodable).
-- The sweep enqueues only originals with no row here. Keys are S3 keys, so they compare
-- byte for byte.
CREATE TABLE image_variant (
  storage_key VARCHAR(256)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
  outcome     VARCHAR(16)   NOT NULL,
  created_at  DATETIME(6)   NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
