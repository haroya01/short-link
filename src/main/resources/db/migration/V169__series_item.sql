-- Series items: the posts and notes of a series in one order. A post or a note belongs to at most
-- one series. posts.series_id stays as the post's membership (feeds collapse a series to its newest
-- episode and the subscription feed reads it), and the item table owns the order; both are written
-- by the same use case. ref_id is polymorphic, so there is no foreign key.
CREATE TABLE series_item (
    id          BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    series_id   BIGINT      NOT NULL,
    item_type   VARCHAR(8)  NOT NULL,
    ref_id      BIGINT      NOT NULL,
    position    INT         NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    UNIQUE KEY uk_series_item_ref (item_type, ref_id),
    KEY idx_series_item_order (series_id, position)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO series_item (series_id, item_type, ref_id, position, created_at)
SELECT p.series_id, 'POST', p.id, COALESCE(p.series_order, 0), NOW(6)
FROM posts p
JOIN series s ON s.id = p.series_id
WHERE p.series_id IS NOT NULL;
