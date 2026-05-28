-- 신고 entity (spec decision #20). 익명 / 로그인 user 둘 다 가능. v0 는 manual triage,
-- CSAM auto-quarantine 은 별도 트랙.

CREATE TABLE abuse_report (
    id                  BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    reporter_user_id    BIGINT       NULL,
    subject_type        VARCHAR(16)  NOT NULL,
    subject_id          BIGINT       NOT NULL,
    reason              VARCHAR(2000) NULL,
    status              VARCHAR(16)  NOT NULL DEFAULT 'OPEN',
    admin_note          VARCHAR(2000) NULL,
    resolved_at         DATETIME(6)  NULL,
    created_at          DATETIME(6)  NOT NULL,
    KEY idx_abuse_report_status_created (status, created_at),
    KEY idx_abuse_report_subject (subject_type, subject_id),
    KEY idx_abuse_report_reporter (reporter_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
