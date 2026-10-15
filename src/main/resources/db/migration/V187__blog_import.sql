-- A blog a member brings in from another platform and keeps in sync. blog_key names the blog on its
-- platform (platform:id), so a member has one row per blog. claim_key holds the blog_key while the
-- member's ownership stands (verified and not disconnected) and is NULL otherwise, so one blog has
-- at most one verified owner. next_fetch_at is never sooner than 30 minutes after the last fetch
-- and backs off after failures; feed_etag and feed_last_modified are the validators of the last
-- fully processed feed. The row goes with the member by ON DELETE CASCADE.
CREATE TABLE blog_import_source (
  id                 BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id            BIGINT       NOT NULL,
  platform           VARCHAR(16)  NOT NULL,
  blog_key           VARCHAR(191) NOT NULL,
  claim_key          VARCHAR(191) NULL,
  blog_url           VARCHAR(512) NOT NULL,
  feed_url           VARCHAR(512) NOT NULL,
  blog_title         VARCHAR(200) NOT NULL,
  status             VARCHAR(24)  NOT NULL,
  verification_code  VARCHAR(16)  NULL,
  verified_at        DATETIME(6)  NULL,
  full_content       BOOLEAN      NOT NULL,
  announce_new_posts BOOLEAN      NOT NULL DEFAULT TRUE,
  show_in_discover   BOOLEAN      NOT NULL DEFAULT TRUE,
  last_synced_at     DATETIME(6)  NULL,
  last_fetched_at    DATETIME(6)  NULL,
  next_fetch_at      DATETIME(6)  NULL,
  fetch_failures     INT          NOT NULL DEFAULT 0,
  feed_etag          VARCHAR(255) NULL,
  feed_last_modified VARCHAR(64)  NULL,
  last_error         VARCHAR(64)  NULL,
  progress_total     INT          NULL,
  progress_done      INT          NULL,
  progress_failed    INT          NULL,
  created_at         DATETIME(6)  NOT NULL,
  updated_at         DATETIME(6)  NOT NULL,
  CONSTRAINT uk_blog_import_source_user_blog UNIQUE (user_id, blog_key),
  CONSTRAINT uk_blog_import_source_claim UNIQUE (claim_key),
  KEY idx_blog_import_source_due (status, next_fetch_at),
  CONSTRAINT fk_blog_import_source_user
    FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Every original a source has taken up, one row per original (url_hash = SHA-256 of its URL), so a
-- sync skips what it already brought in even after the member deleted that post. imported_at is
-- set once the post is published; until then a failed attempt is retried up to a limit, resuming
-- the draft post_id points at. Rows go with the source; post_id is cleared when the post goes.
CREATE TABLE blog_import_item (
  id          BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
  source_id   BIGINT      NOT NULL,
  url_hash    VARCHAR(64) NOT NULL,
  post_id     BIGINT      NULL,
  attempts    INT         NOT NULL DEFAULT 0,
  imported_at DATETIME(6) NULL,
  created_at  DATETIME(6) NOT NULL,
  CONSTRAINT uk_blog_import_item_source_url UNIQUE (source_id, url_hash),
  KEY idx_blog_import_item_post (post_id),
  CONSTRAINT fk_blog_import_item_source
    FOREIGN KEY (source_id) REFERENCES blog_import_source (id) ON DELETE CASCADE,
  CONSTRAINT fk_blog_import_item_post
    FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- A post brought in from a blog keeps where it came from. source_url_hash (SHA-256 of source_url)
-- allows one post per original per author. hidden_from_discover copies the source's show_in_discover onto its posts so the discovery feeds
-- filter on the post row alone. import_source_id and source_link_id are cleared when the source or
-- the short link is deleted; a post without source_link_id shows no short link.
ALTER TABLE posts
  ADD COLUMN import_source_id     BIGINT        NULL,
  ADD COLUMN source_platform      VARCHAR(16)   NULL,
  ADD COLUMN source_url           VARCHAR(2048) NULL,
  ADD COLUMN source_url_hash      VARCHAR(64)   NULL,
  ADD COLUMN source_excerpt_only  BOOLEAN       NOT NULL DEFAULT FALSE,
  ADD COLUMN source_link_id       BIGINT        NULL,
  ADD COLUMN source_short_code    VARCHAR(16)   NULL,
  ADD COLUMN hidden_from_discover BOOLEAN       NOT NULL DEFAULT FALSE;

ALTER TABLE posts
  ADD CONSTRAINT uk_posts_user_source_url UNIQUE (user_id, source_url_hash),
  ADD KEY idx_posts_import_source (import_source_id),
  ADD KEY idx_posts_source_link (source_link_id),
  ADD CONSTRAINT fk_posts_import_source
    FOREIGN KEY (import_source_id) REFERENCES blog_import_source (id) ON DELETE SET NULL,
  ADD CONSTRAINT fk_posts_source_link
    FOREIGN KEY (source_link_id) REFERENCES link (id) ON DELETE SET NULL;
