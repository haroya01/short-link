-- Sec-Fetch-Site — 레퍼러가 지워진 클릭을 둘로 가른다.
--
-- 지금 referrer 없는 클릭은 QR 스캔·주소 직접 입력·레퍼러를 떼는 메신저가 전부 'direct' 한 덩어리다.
-- 브라우저가 보내는 Sec-Fetch-Site 는 레퍼러가 없어도 none(사용자가 직접 연 것: 타이핑·북마크·QR) 과
-- cross-site(어딘가에서 링크를 눌러 온 것) 를 구분해 준다 — 다크 소셜 판정의 해상도가 그만큼 올라간다.
--
-- 표준이 정의한 네 값(none/cross-site/same-site/same-origin) 만 저장하며, 브라우저가 표준으로 보내는
-- 저엔트로피 신호라 방문자를 특정하지 않는다(기존 IP 마스킹·GPC 존중 정책과 충돌 없음). 지문화 위험이 있는
-- Sec-CH-UA·Viewport·DPR 계열은 수집하지 않는다.
--
-- 백필 없음: 헤더는 요청 시점에만 존재하고 과거 행에서 되살릴 방법이 없다. 도입 이전 클릭은 NULL 로 남으며
-- 집계에서 그냥 빠진다(모르는 것을 아는 척하지 않는다).
ALTER TABLE click_event ADD COLUMN fetch_site VARCHAR(16) NULL;

-- client_app 과 같은 이유로 (link_id, is_bot) 뒤에 붙인다 — 링크 단위 사람 클릭 GROUP BY 가 인덱스에서 끝난다.
CREATE INDEX idx_click_event_link_bot_fetch_site ON click_event (link_id, is_bot, fetch_site);
