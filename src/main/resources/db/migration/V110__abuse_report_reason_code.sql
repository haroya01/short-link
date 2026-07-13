-- 신고 사유 하이브리드: enum 코드(reason_code) + 자유서술(detail, 기존 reason 컬럼 재사용).
-- iOS/웹의 6종 사유(SPAM/HARASSMENT/VIOLENCE/SEXUAL/COPYRIGHT/OTHER)를 정형 코드로 받고,
-- 기존 free-text 는 detail 로 계속 저장(엔티티 필드 detail -> DB 컬럼 reason 매핑, 컬럼명 유지로 데이터 이관 없음).
-- 출시 전 기존 행에는 코드가 없으므로 NULL 허용(뷰에서 코드 없는 legacy 로 렌더).
ALTER TABLE abuse_report ADD COLUMN reason_code VARCHAR(16) NULL AFTER subject_id;
