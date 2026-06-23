-- 링크 소유자 푸시 알림 옵트아웃. 행이 없으면 켜짐(기본 on) — enabled=false 면 그 종류만 끈다.
-- (user_id, type) 쌍은 유일. 종류: FIRST_CLICK, MILESTONE, VELOCITY_SPIKE, EXPIRY_IMMINENT, DIGEST.
CREATE TABLE notification_preference (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    type       VARCHAR(32) NOT NULL,
    enabled    BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_notification_preference UNIQUE (user_id, type)
);

-- 한 사용자의 알림 설정 조회용.
CREATE INDEX idx_notification_preference_user ON notification_preference (user_id);
