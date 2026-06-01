-- Author curation: pinned posts surface first on the author's public profile, in pin_order.
-- NULL = not pinned. Small per-author set; ordering is done in the query layer.
ALTER TABLE posts ADD COLUMN pin_order INT NULL;
