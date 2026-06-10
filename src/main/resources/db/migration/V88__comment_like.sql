-- 댓글 좋아요. (comment_id, user_id) 유니크 = 한 사용자당 한 번. 카운트는 비정규화 없이
-- 목록 시점 GROUP BY (글 하나의 댓글 수십 개 규모라 충분).
-- comment FK 는 ON DELETE CASCADE: 댓글 삭제 경로(작성자 삭제·답글 캐스케이드·글 삭제)가
-- 여러 갈래라, 자바 쪽 정리 코드를 늘리는 대신 DB 가 한 곳에서 무결성을 보장한다.
CREATE TABLE comment_like (
    id          BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    comment_id  BIGINT      NOT NULL,
    user_id     BIGINT      NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    UNIQUE KEY uk_comment_like_comment_user (comment_id, user_id),
    KEY idx_comment_like_user (user_id),
    CONSTRAINT fk_comment_like_comment FOREIGN KEY (comment_id) REFERENCES comment(id) ON DELETE CASCADE,
    CONSTRAINT fk_comment_like_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
