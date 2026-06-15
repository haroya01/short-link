-- 읽기 기록(Medium식 reading history). 로그인 사용자가 발행 글을 읽으면 한 행 — (user_id, post_id)
-- 유니크라 다시 읽으면 read_at 만 갱신(업서트). 비공개·삭제된 글은 목록에서 빠진다. 익명 뷰
-- 비콘(post_view_event)과 별개: 그건 작가 분석용 집계, 이건 독자 본인의 사적 기록(PII)이라
-- 계정 삭제 시 wipe 된다.
CREATE TABLE post_read (
    id          BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT      NOT NULL,
    post_id     BIGINT      NOT NULL,
    read_at     DATETIME(6) NOT NULL,
    UNIQUE KEY uk_post_read_user_post (user_id, post_id),
    KEY idx_post_read_user_read_at (user_id, read_at),
    CONSTRAINT fk_post_read_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
