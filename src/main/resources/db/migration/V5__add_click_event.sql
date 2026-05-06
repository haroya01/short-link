CREATE TABLE click_event (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    link_id BIGINT NOT NULL,
    clicked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    referrer TEXT,
    user_agent TEXT,
    client_ip VARCHAR(45),
    KEY idx_click_event_link_id_clicked_at (link_id, clicked_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
