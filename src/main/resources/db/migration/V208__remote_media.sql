-- A copy in this server's bucket of a JPEG or PNG attached to a note from another server, so readers
-- load it and its resized copies from the CDN instead of from that server. A note_media row showing
-- a copy holds its key in storage_key, the CDN address in url and the other server's address in
-- remote_url; one copy serves every row with the same address. A copy is dropped once no
-- note_media row shows it, or 30 days after cached_at, when its rows go back to remote_url.
-- Nothing here is read by the release before this one, which keeps working on this schema.
CREATE TABLE remote_media (
  storage_key VARCHAR(256) NOT NULL PRIMARY KEY,
  remote_url  VARCHAR(512) NOT NULL,
  cached_at   DATETIME(6)  NOT NULL,
  KEY idx_remote_media_cached_at (cached_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE note_media ADD COLUMN remote_url VARCHAR(512) NULL;

ALTER TABLE note_media ADD KEY idx_note_media_storage_key (storage_key);
