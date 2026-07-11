-- 블로그 벨 알림 종류별 옵트아웃. 행이 없으면 켜짐(기본 on) — enabled=false 면 그 종류만 끈다.
-- (user_id, type) 쌍은 유일. 종류: LIKE, COMMENT, FOLLOW, SERIES_SUBSCRIBE, REPLY, NEW_POST, MENTION.
-- 링크 알림 옵트아웃(notification_preference) 과는 별개 도메인 — 종류 집합과 집행 경로가 다르다.
CREATE TABLE blog_notification_preference (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    type       VARCHAR(32) NOT NULL,
    enabled    BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_blog_notification_preference UNIQUE (user_id, type)
);

-- 한 사용자의 알림 설정 조회용.
CREATE INDEX idx_blog_notification_preference_user ON blog_notification_preference (user_id);
