-- Post series — named, ordered grouping of an author's posts (velog-style). Membership +
-- ordering live on posts (series_id / series_order) so a post can move between/within series
-- without touching the series row. series_id is a soft reference (no FK) so deleting a series
-- just leaves orphaned ids briefly until the delete use case nulls them.

CREATE TABLE series (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    slug        VARCHAR(200) NOT NULL,
    title       VARCHAR(200) NOT NULL,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    UNIQUE KEY uk_series_user_slug (user_id, slug),
    CONSTRAINT fk_series_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE posts
    ADD COLUMN series_id    BIGINT NULL,
    ADD COLUMN series_order INT    NULL,
    ADD KEY idx_posts_series (series_id, series_order);
