CREATE TABLE audit_log (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    actor_user_id BIGINT NULL,
    action VARCHAR(50) NOT NULL,
    target_type VARCHAR(30) NOT NULL,
    target_id VARCHAR(64) NULL,
    metadata JSON NULL,
    request_id VARCHAR(40) NULL,
    occurred_at DATETIME(6) NOT NULL,
    KEY idx_audit_actor_occurred (actor_user_id, occurred_at),
    KEY idx_audit_action_occurred (action, occurred_at),
    KEY idx_audit_target (target_type, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
