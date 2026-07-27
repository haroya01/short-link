-- 인앱 브라우저(카카오톡·인스타그램·라인 …) 안에서 열린 클릭을 따로 센다.
--
-- user_agent 원문은 V5 부터 저장만 되고 어디서도 읽히지 않았다. 인앱 웹뷰는 UA 에 자기 토큰을 얹으므로,
-- 쓰기 시점에 그 토큰을 한 번 분류해 client_app 에 넣어 두면 조회 때 TEXT LIKE 풀스캔을 하지 않아도 된다.
-- 일반 브라우저(사파리·크롬 등)는 NULL — "인앱이 아님"이 기본값이라 새 컬럼이 기존 행을 건드리지 않는다.
--
-- 봇과는 무관한 축이다. 인앱 브라우저로 연 사람은 사람이고, is_bot 은 그대로 둔다. 집계는 항상
-- client_app IS NOT NULL AND is_bot = FALSE 로 사람 클릭만 본다.
ALTER TABLE click_event ADD COLUMN client_app VARCHAR(32) NULL;

-- 조회는 언제나 링크 단위 + 사람만이다. idx_click_event_link_bot_time(link_id, is_bot, clicked_at) 로도
-- 링크 범위는 좁혀지지만, 인앱 비중이 낮은 링크에서 client_app IS NOT NULL 을 힙에서 걸러야 한다.
-- (link_id, is_bot, client_app) 이면 GROUP BY 까지 인덱스에서 끝난다.
CREATE INDEX idx_click_event_link_bot_client_app ON click_event (link_id, is_bot, client_app);

-- 과거 데이터 백필. 대표 앱만 — 마이너 웹뷰까지 넣으려고 LIKE 를 늘리면 대용량 테이블 풀스캔 UPDATE 가
-- 그만큼 길어진다. 나머지는 이 마이그레이션 이후 들어오는 클릭부터 쓰기 시점 분류로 채워진다.
-- 순서에 의미가 있다: 페이스북 인앱은 UA 에 Instagram 과 FBAV 를 함께 싣는 경우가 있어 인스타그램을 먼저
-- 확정하고, 그 다음에 남은 행만 페이스북으로 본다(뒤 UPDATE 의 client_app IS NULL 조건).
UPDATE click_event SET client_app = 'kakaotalk'
 WHERE client_app IS NULL AND user_agent LIKE '%KAKAOTALK%';

UPDATE click_event SET client_app = 'instagram'
 WHERE client_app IS NULL AND user_agent LIKE '%Instagram%';

UPDATE click_event SET client_app = 'line'
 WHERE client_app IS NULL AND user_agent LIKE '%Line/%';

UPDATE click_event SET client_app = 'facebook'
 WHERE client_app IS NULL AND (user_agent LIKE '%FBAV%' OR user_agent LIKE '%FB_IAB%');

UPDATE click_event SET client_app = 'naver'
 WHERE client_app IS NULL AND user_agent LIKE '%NAVER(inapp%';

UPDATE click_event SET client_app = 'daum'
 WHERE client_app IS NULL AND user_agent LIKE '%DaumApps%';

UPDATE click_event SET client_app = 'tiktok'
 WHERE client_app IS NULL AND (user_agent LIKE '%musical\_ly%' OR user_agent LIKE '%TikTok%');

UPDATE click_event SET client_app = 'twitter'
 WHERE client_app IS NULL AND user_agent LIKE '%Twitter%';
