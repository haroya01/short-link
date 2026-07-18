-- 독자 행동 이벤트 로그 — post_view_event(도달)와 짝을 이루는 "행동" 계층. 스크롤 깊이·체류·두 번째
-- 행동(다른 글/연결/프로필/시리즈/태그 클릭)을 세션 단위로 남겨, 유입→읽기→다음 행동 퍼널을 만든다.
-- post FK 없음: 비콘 배치 삽입이 삭제 경합으로 통째로 죽지 않게 하고, 잔존 행은 90일 보존 청소가 걷는다.
-- props 같은 자유 컬럼은 두지 않는다 — 필요해지면 그때 마이그레이션으로 붙인다.
CREATE TABLE behavior_event (
    id           BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    event_name   VARCHAR(32) NOT NULL,
    occurred_at  DATETIME(6) NOT NULL,
    session_id   VARCHAR(40) NULL,
    post_id      BIGINT      NULL,
    target_type  VARCHAR(32) NULL,
    target_id    VARCHAR(64) NULL,
    depth_pct    SMALLINT    NULL,
    dwell_ms     BIGINT      NULL,
    device_class VARCHAR(32) NULL,
    is_bot       BOOLEAN     NOT NULL DEFAULT FALSE,
    bot_name     VARCHAR(64) NULL,
    visitor_hash VARCHAR(64) NULL
);

CREATE INDEX idx_behavior_event_name_time ON behavior_event (event_name, occurred_at);
CREATE INDEX idx_behavior_event_post_time ON behavior_event (post_id, occurred_at);
CREATE INDEX idx_behavior_event_session ON behavior_event (session_id);
-- 보존 청소(occurred_at < cutoff 삭제)가 풀스캔 안 타게.
CREATE INDEX idx_behavior_event_time ON behavior_event (occurred_at);

-- 조회(도달) 이벤트에도 세션을 실어, 유입 레퍼러(post_view_event)와 행동(behavior_event)을
-- 같은 세션으로 조인할 수 있게 한다. 기존 행은 NULL — 세션 개념 도입 전 데이터.
ALTER TABLE post_view_event ADD COLUMN session_id VARCHAR(40) NULL;
CREATE INDEX idx_post_view_event_session ON post_view_event (session_id);
