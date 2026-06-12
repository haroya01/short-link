-- 짧은 글(노트). 글(post)과 달리 제목·슬러그·블록·발행 상태가 없다 — 쓰는 즉시 공개,
-- 지우면 hard delete. 피드는 id DESC(PK 역순)라 별도 정렬 인덱스가 필요 없다.
CREATE TABLE note (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    body        VARCHAR(500) NOT NULL,
    created_at  DATETIME(6)  NOT NULL,
    KEY idx_note_user (user_id),
    CONSTRAINT fk_note_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 노트 좋아요 — comment_like(V88)와 같은 문법: (note, user) 유니크가 곧 상태.
CREATE TABLE note_like (
    id          BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    note_id     BIGINT      NOT NULL,
    user_id     BIGINT      NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    UNIQUE KEY uk_note_like_note_user (note_id, user_id),
    KEY idx_note_like_user (user_id),
    CONSTRAINT fk_note_like_note FOREIGN KEY (note_id) REFERENCES note(id) ON DELETE CASCADE,
    CONSTRAINT fk_note_like_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
