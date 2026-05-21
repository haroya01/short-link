-- 오프라인 QR 배포 캠페인 도메인. Campaign 은 기간(startsAt/endsAt)과 종료 정책(postEndAction)
-- 을 가진 운영 단위, CampaignBatch 는 배포 묶음(배포자/지역/수량)이며 각 batch 는 대표 link
-- 1개를 갖는다 (Batch:Link = 1:1, Batch:인쇄물 = 1:N — quantity 는 인쇄/배포 수량 메타데이터).
-- Link 에는 expired_redirect_url 을 추가 — Campaign 이 ENDED 로 전환될 때 batch link 에
-- 일괄 박혀, redirect hot path 가 Campaign/Batch 조회 없이 Link 만 보고 동작하게 한다.

CREATE TABLE campaign (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    owner_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    starts_at DATETIME(6) NOT NULL,
    ends_at DATETIME(6) NOT NULL,
    ended_at DATETIME(6) NULL,
    default_destination_url VARCHAR(2048) NULL,
    post_end_action VARCHAR(16) NOT NULL DEFAULT 'KEEP',
    post_end_destination_url VARCHAR(2048) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    KEY idx_campaign_owner_created (owner_id, created_at),
    KEY idx_campaign_status_ends (status, ends_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE campaign_batch (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    campaign_id BIGINT NOT NULL,
    link_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    distributor_name VARCHAR(255) NULL,
    area_label VARCHAR(255) NULL,
    quantity INT NOT NULL,
    memo VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_campaign_batch_link (link_id),
    KEY idx_campaign_batch_campaign (campaign_id),
    CONSTRAINT fk_campaign_batch_campaign
        FOREIGN KEY (campaign_id) REFERENCES campaign(id) ON DELETE CASCADE,
    CONSTRAINT fk_campaign_batch_link
        FOREIGN KEY (link_id) REFERENCES link(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE link
    ADD COLUMN expired_redirect_url VARCHAR(2048) NULL AFTER expired_message;
