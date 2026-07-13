-- 중복 신고 가드 조회용 인덱스: 같은 신고자가 같은 대상에 대해 이미 OPEN 신고를 냈는지 빠르게 확인.
-- (신고자, 대상종류, 대상id) 로 조회 후 status=OPEN 존재검사. status 는 낮은 카디널리티라 인덱스에 포함하지 않는다.
-- 부분 UNIQUE(OPEN 만) 는 MySQL 이 지원하지 않아 애플리케이션 레벨 존재검사로 강제한다.
CREATE INDEX idx_abuse_report_dedup ON abuse_report (reporter_user_id, subject_type, subject_id);
