-- 하이라이트 아래 평면(1단) 답글 스레드. note(오프너) 옆에 독자들이 답글을 단다.
-- post_highlight FK 는 ON DELETE CASCADE: 하이라이트 삭제(작성자 삭제·계정 정리)가 답글을
-- 함께 거둬가도록 DB 가 한 곳에서 무결성을 보장한다(comment_like 와 동일 전략).
-- 카운트는 비정규화 없이 목록 시점 GROUP BY (하이라이트 하나당 답글 소수 규모라 충분).
CREATE TABLE highlight_reply (
    id            BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    highlight_id  BIGINT        NOT NULL,
    user_id       BIGINT        NOT NULL,
    body          VARCHAR(2000) NOT NULL,
    created_at    DATETIME(6)   NOT NULL,
    updated_at    DATETIME(6)   NOT NULL,
    KEY idx_highlight_reply_highlight_created (highlight_id, created_at),
    KEY idx_highlight_reply_user (user_id),
    CONSTRAINT fk_highlight_reply_highlight FOREIGN KEY (highlight_id) REFERENCES post_highlight(id) ON DELETE CASCADE,
    CONSTRAINT fk_highlight_reply_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
