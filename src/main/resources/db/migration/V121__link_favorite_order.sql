-- Favorites share the link lifetime: deletion cannot leave an orphan favorite.
ALTER TABLE link ADD COLUMN favorite_order INT NULL;
CREATE INDEX idx_link_user_favorite_order ON link (user_id, favorite_order, id);
