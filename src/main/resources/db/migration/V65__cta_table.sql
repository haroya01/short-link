-- Reusable CTA library. 작성자 별 라이브러리 entity. PostBlock CTA_REF 또는 ProfileBlock (v1+) 에서 참조.
-- 소프트 삭제 (deleted_at) — 분석 데이터 보존 + 과거 발행 글의 참조 무결성.

CREATE TABLE cta (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    label       VARCHAR(100) NOT NULL,
    url         VARCHAR(2048) NOT NULL,
    style       VARCHAR(16)  NOT NULL DEFAULT 'PRIMARY',
    purpose     VARCHAR(16)  NOT NULL DEFAULT 'CUSTOM',
    deleted_at  DATETIME(6)  NULL,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    KEY idx_cta_user_active (user_id, deleted_at),
    KEY idx_cta_user_created (user_id, created_at),
    CONSTRAINT fk_cta_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
