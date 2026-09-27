-- 모집/이벤트 신청 페이지. 발행 즉시 공개(OPEN), 참가자는 비로그인 신청.
-- 정원 판정은 event.registration_count 조건부 원자 UPDATE 로 — 행 잠금·경합 없이 마지막 1자리를 정확히 처리.
CREATE TABLE event (
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    user_id            BIGINT        NOT NULL,
    slug               VARCHAR(16)   NOT NULL,
    title              VARCHAR(200)  NOT NULL,
    description_md     MEDIUMTEXT    NULL,
    cover_image_key    VARCHAR(255)  NULL,
    starts_at          DATETIME(6)   NOT NULL,
    ends_at            DATETIME(6)   NULL,
    timezone           VARCHAR(40)   NOT NULL,
    location_text      VARCHAR(200)  NULL,
    location_url       VARCHAR(2048) NULL,
    online_url         VARCHAR(2048) NULL,
    capacity           INT           NULL,
    close_at           DATETIME(6)   NULL,
    contact_field      VARCHAR(16)   NOT NULL,
    status             VARCHAR(16)   NOT NULL,
    registration_count INT           NOT NULL DEFAULT 0,
    primary_link_id    BIGINT        NULL,
    pii_purged_at      DATETIME(6)   NULL,
    created_at         DATETIME(6)   NOT NULL,
    updated_at         DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_event_slug UNIQUE (slug)
);

CREATE INDEX idx_event_user ON event (user_id);
-- PII 파기 스케줄러가 "종료 후 30일 지난 미파기 이벤트"를 훑는 경로.
CREATE INDEX idx_event_purge ON event (pii_purged_at, ends_at);

CREATE TABLE event_question (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    event_id     BIGINT       NOT NULL,
    position     INT          NOT NULL,
    type         VARCHAR(16)  NOT NULL,
    label        VARCHAR(200) NOT NULL,
    options_json TEXT         NULL,
    required     BIT(1)       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_event_question_event FOREIGN KEY (event_id) REFERENCES event (id)
);

CREATE INDEX idx_event_question_event ON event_question (event_id, position);

-- 이벤트 하나에 배포 채널별 별칭 단축링크 여러 개 ("단톡용", "QR용" …). primary 링크도 여기 들어간다.
CREATE TABLE event_link (
    id       BIGINT      NOT NULL AUTO_INCREMENT,
    event_id BIGINT      NOT NULL,
    link_id  BIGINT      NOT NULL,
    label    VARCHAR(50) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_event_link UNIQUE (event_id, link_id),
    CONSTRAINT fk_event_link_event FOREIGN KEY (event_id) REFERENCES event (id)
);

-- 신청은 계정 없이 받으므로 개인정보(name/contact/answers)는 이벤트 종료 30일 후 스케줄러가 파기한다.
-- (event_id, contact) 유일 — 취소 후 재신청은 CANCELED 행을 CONFIRMED 로 되살리는 방식으로 처리 (MySQL 은 partial index 미지원).
-- 채널 스냅샷(source_channel/client_app/referrer_host/utm_source)은 클릭 매칭 시점에 비정규화 — 대시보드가 click_event 조인 없이 집계.
CREATE TABLE event_registration (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    event_id          BIGINT       NOT NULL,
    name              VARCHAR(100) NOT NULL,
    contact           VARCHAR(254) NOT NULL,
    answers_json      TEXT         NULL,
    status            VARCHAR(16)  NOT NULL,
    cancel_token_hash CHAR(64)     NOT NULL,
    link_id           BIGINT       NULL,
    source_channel    VARCHAR(32)  NULL,
    client_app        VARCHAR(32)  NULL,
    referrer_host     VARCHAR(255) NULL,
    utm_source        VARCHAR(255) NULL,
    visitor_hash      CHAR(64)     NULL,
    created_at        DATETIME(6)  NOT NULL,
    canceled_at       DATETIME(6)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_event_registration_contact UNIQUE (event_id, contact),
    CONSTRAINT uk_event_registration_cancel_token UNIQUE (cancel_token_hash),
    CONSTRAINT fk_event_registration_event FOREIGN KEY (event_id) REFERENCES event (id)
);

CREATE INDEX idx_event_registration_event ON event_registration (event_id, status, created_at);
