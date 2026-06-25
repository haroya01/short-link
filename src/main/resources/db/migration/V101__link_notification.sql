-- 링크 알림 인박스 — 첫 클릭·마일스톤·급증·곧 만료를 인앱 목록으로 남긴다(블로그 notification 과 별개 도메인).
-- 푸시는 LinkNotificationDispatcher 가 따로 보내고, 그 발송 전에 이 행을 저장한다 — 푸시를 꺼 둬도 인박스엔 남는다.
CREATE TABLE link_notification (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    recipient_user_id BIGINT       NOT NULL,
    type              VARCHAR(32)  NOT NULL,
    short_code        VARCHAR(64),
    subtitle          VARCHAR(200),
    body              VARCHAR(500),
    read_at           DATETIME(6),
    created_at        DATETIME(6)  NOT NULL,
    PRIMARY KEY (id)
);

-- 받은 사람의 최신 목록(커서 = id 내림차순)용.
CREATE INDEX idx_link_notification_recipient ON link_notification (recipient_user_id, id);
-- 안 읽음 카운트(read_at IS NULL)용.
CREATE INDEX idx_link_notification_unread ON link_notification (recipient_user_id, read_at);
