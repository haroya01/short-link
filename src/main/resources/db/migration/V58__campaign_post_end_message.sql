-- Campaign 의 postEndAction=EXPIRE 일 때 batch link 의 만료 페이지에 박힐 메시지.
-- KEEP/REDIRECT 일 때는 저장만 되고 실제 link 에는 propagate 되지 않는다 (CampaignService 에서 분기).
-- link.expired_message 와 동일하게 VARCHAR(500).
ALTER TABLE campaign
    ADD COLUMN post_end_message VARCHAR(500) NULL AFTER post_end_destination_url;
