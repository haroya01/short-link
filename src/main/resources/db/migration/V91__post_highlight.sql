-- 리더 하이라이트(Medium식 소셜 하이라이트). 본문 텍스트 스팬을 block_order + 문자 offset 으로
-- 앵커한다(클라가 블록을 order 로 렌더하므로 PK 가 아니라 order 가 안정적). quote 는 작성 시점
-- 스냅샷이라 글이 편집돼도 하이라이트가 스테일되지 않는다. 공개+귀속: 누구나 누가 어디를
-- 하이라이트했는지 본다. (post_id, user_id, block_order, start_offset, end_offset) 유니크 —
-- 같은 독자가 같은 스팬을 두 번 못 만든다.
CREATE TABLE post_highlight (
    id            BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    post_id       BIGINT        NOT NULL,
    user_id       BIGINT        NOT NULL,
    block_order   INT           NOT NULL,
    start_offset  INT           NOT NULL,
    end_offset    INT           NOT NULL,
    quote         VARCHAR(1000) NOT NULL,
    created_at    DATETIME(6)   NOT NULL,
    UNIQUE KEY uk_post_highlight_span (post_id, user_id, block_order, start_offset, end_offset),
    KEY idx_post_highlight_post (post_id, block_order),
    KEY idx_post_highlight_user (user_id, created_at),
    CONSTRAINT fk_post_highlight_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
