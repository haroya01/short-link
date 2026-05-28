-- Content platform v0 — Post 도메인 신설. 헥사고날 D6 컨벤션 기준 post/ top-level 도메인.
-- Profile 시스템과 sibling, 동일 user_id 로 묶임. PostBlock = 본문 블록 (B2 미니멀 에디터),
-- PostRevision = (b) 발행 스냅샷만 (diff 뷰 v0 X).

CREATE TABLE posts (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    slug            VARCHAR(200) NOT NULL,
    title           VARCHAR(200) NOT NULL,
    status          VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',
    language_tag    VARCHAR(16)  NOT NULL DEFAULT 'ko',
    published_at    DATETIME(6)  NULL,
    scheduled_at    DATETIME(6)  NULL,
    excerpt         VARCHAR(500) NULL,
    og_image_url    VARCHAR(512) NULL,
    og_image_key    VARCHAR(256) NULL,
    view_count      BIGINT       NOT NULL DEFAULT 0,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    UNIQUE KEY uk_posts_user_slug (user_id, slug),
    KEY idx_posts_user_status (user_id, status),
    KEY idx_posts_user_published (user_id, published_at),
    KEY idx_posts_scheduled (status, scheduled_at),
    CONSTRAINT fk_posts_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE post_block (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    post_id         BIGINT       NOT NULL,
    block_type      VARCHAR(16)  NOT NULL,
    content         TEXT         NULL,
    block_order     INT          NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    KEY idx_post_block_post_order (post_id, block_order),
    CONSTRAINT fk_post_block_post FOREIGN KEY (post_id) REFERENCES posts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE post_revision (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    post_id         BIGINT       NOT NULL,
    version_number  INT          NOT NULL,
    title_snapshot  VARCHAR(200) NOT NULL,
    content_json    LONGTEXT     NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    UNIQUE KEY uk_post_revision_post_version (post_id, version_number),
    KEY idx_post_revision_post_created (post_id, created_at),
    CONSTRAINT fk_post_revision_post FOREIGN KEY (post_id) REFERENCES posts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
