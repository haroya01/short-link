-- APNs 디바이스 토큰. token 이 유니크 키 — 같은 기기에 다른 계정이 로그인하면
-- 소유자를 갈아끼운다(이전 계정으로의 오발송 방지). 로그아웃/탈퇴 시 행 삭제.
CREATE TABLE device_token (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    token       VARCHAR(200) NOT NULL,
    platform    VARCHAR(16)  NOT NULL,
    created_at  DATETIME(6)  NOT NULL,
    UNIQUE KEY uk_device_token (token),
    KEY idx_device_token_user (user_id),
    CONSTRAINT fk_device_token_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
