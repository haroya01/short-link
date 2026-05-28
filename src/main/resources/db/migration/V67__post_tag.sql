-- Post tags — velog-style freeform tags, owner-ordered. Side table for the PostEntity
-- @ElementCollection. ordinal preserves author ordering; (post_id, ordinal) is the natural key.
-- idx_post_tag_tag supports future tag-filtered listing.

CREATE TABLE post_tag (
    post_id  BIGINT      NOT NULL,
    ordinal  INT         NOT NULL,
    tag      VARCHAR(40) NOT NULL,
    PRIMARY KEY (post_id, ordinal),
    KEY idx_post_tag_tag (tag),
    CONSTRAINT fk_post_tag_post FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
