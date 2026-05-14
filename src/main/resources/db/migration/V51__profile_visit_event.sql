-- Per-visit event log for public profile pages. Mirrors click_event minus link-specific fields
-- (link_id / destination_id) and indexed by profile_user_id so the owner's stats query stays
-- on a covering index. Visitor hash + visited_at composite for cheap "unique visitors per day"
-- queries without scanning the full row.
CREATE TABLE profile_visit_event (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    profile_user_id BIGINT NOT NULL,
    visited_at DATETIME(6) NOT NULL,
    referrer TEXT,
    referrer_host VARCHAR(255),
    user_agent TEXT,
    client_ip VARCHAR(45),
    utm_source VARCHAR(255),
    utm_medium VARCHAR(255),
    utm_campaign VARCHAR(255),
    utm_term VARCHAR(255),
    utm_content VARCHAR(255),
    device_class VARCHAR(32),
    os_name VARCHAR(64),
    browser_name VARCHAR(64),
    is_bot BOOLEAN NOT NULL DEFAULT FALSE,
    bot_name VARCHAR(64),
    country_code CHAR(2),
    region_name VARCHAR(128),
    city_name VARCHAR(128),
    language VARCHAR(8),
    visitor_hash CHAR(64),
    source_channel VARCHAR(40),
    asn INT,
    asn_org VARCHAR(200),
    KEY idx_profile_visit_owner_time (profile_user_id, visited_at),
    KEY idx_profile_visit_visitor (visitor_hash, profile_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
