package com.example.short_link.campaign.application.dto;

import com.example.short_link.campaign.domain.CampaignEntity;

public record CampaignSummary(
    CampaignEntity campaign, long batchCount, long totalQuantity, long humanClicks) {}
