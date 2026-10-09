-- Advanced by every write that changes what the author edits (body, metadata, revision restore, admin
-- title/tag edit), so a save based on an older copy is refused instead of silently overwriting the
-- other device's edit. Status changes, views, likes, pins and series moves leave it untouched.
ALTER TABLE posts ADD COLUMN content_version BIGINT NOT NULL DEFAULT 0;
