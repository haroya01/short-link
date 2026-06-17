-- 브라우저 웹푸시 구독 — 인앱 알림과 같은 내용을 데스크톱/안드로이드 브라우저로도 보낸다(APNs 의
-- 웹 짝). endpoint 가 유니크 — 같은 브라우저가 재구독하면 소유자를 갈아끼운다(오발송 방지).
-- 로그아웃·구독해제·404/410 응답 시 삭제. VAPID 미설정이면 발송기는 no-op.
CREATE TABLE web_push_subscription (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    endpoint   VARCHAR(512) NOT NULL,
    p256dh     VARCHAR(255) NOT NULL,
    auth       VARCHAR(255) NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_web_push_endpoint UNIQUE (endpoint)
);

-- 한 사용자의 모든 구독 조회(발송 시 fan-out)용.
CREATE INDEX idx_web_push_user ON web_push_subscription (user_id);
