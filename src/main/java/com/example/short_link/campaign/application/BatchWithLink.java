package com.example.short_link.campaign.application;

import com.example.short_link.campaign.domain.CampaignBatchEntity;

public record BatchWithLink(CampaignBatchEntity batch, BatchLinkInfo link) {}
