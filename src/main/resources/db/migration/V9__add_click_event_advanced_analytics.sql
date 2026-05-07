ALTER TABLE click_event
    ADD COLUMN bot_name VARCHAR(64),
    ADD COLUMN region_name VARCHAR(128),
    ADD COLUMN city_name VARCHAR(128),
    ADD COLUMN language CHAR(8),
    ADD COLUMN visitor_hash CHAR(64),
    ADD COLUMN referrer_host VARCHAR(255),
    ADD INDEX idx_click_event_visitor (link_id, visitor_hash);
