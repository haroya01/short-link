package com.example.short_link.campaign.infrastructure.persistence;

import com.example.short_link.campaign.domain.CampaignEntity;
import com.example.short_link.campaign.domain.repository.CampaignRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaCampaignRepository
    extends JpaRepository<CampaignEntity, Long>, CampaignRepository {}
