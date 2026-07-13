-- 유저 제재 상태: ACTIVE(기본) / SUSPENDED(임시·만료시각) / BANNED(영구).
-- SUSPENDED 는 suspended_until 까지 콘텐츠 생성 차단(로그인은 허용, 사용자가 상태를 확인·소명 가능).
-- BANNED 는 로그인·콘텐츠 생성 모두 차단. 관리자 모더레이션에서만 전이된다.
ALTER TABLE users
    ADD COLUMN moderation_status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN suspended_until   DATETIME(6) NULL;
