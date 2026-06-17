-- 사용자 차단 — App Store 1.2(UGC) 요건. blocker 가 blocked 를 차단하면 그 사용자의 콘텐츠
-- (글·댓글·하이라이트·노트)을 더는 보지 않는다. (blocker_id, blocked_id) 쌍은 유일.
CREATE TABLE user_block (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    blocker_id BIGINT      NOT NULL,
    blocked_id BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_user_block UNIQUE (blocker_id, blocked_id)
);

-- blocker 의 차단 목록 조회·필터링용.
CREATE INDEX idx_user_block_blocker ON user_block (blocker_id);
