-- Set when an admin takes a post down (abuse report or admin unpublish). While it is set the author
-- can still edit the post but cannot make it public again; only an admin release clears it. The
-- status stays UNPUBLISHED (DRAFT if it was never published), so every status-based read keeps
-- treating it as not public without change. Earlier takedowns left no record, so nothing is backfilled.
ALTER TABLE posts ADD COLUMN taken_down_at DATETIME(6) NULL;
