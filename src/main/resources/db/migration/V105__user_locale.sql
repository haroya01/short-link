-- 사용자 선호 로케일 — 서버가 조합하는 푸시 알림을 수신자 언어로 렌더한다(모르면 ko).
-- Accept-Language 로 웹푸시 구독/기기 등록 시점에 채워진다.
ALTER TABLE users ADD COLUMN locale VARCHAR(16) NOT NULL DEFAULT 'ko';
