package com.example.short_link.campaign.application.dto;

import com.example.short_link.campaign.domain.CampaignBatchEntity;
import com.example.short_link.link.application.dto.LinkRef;

public record BatchWithLink(CampaignBatchEntity batch, LinkRef link) {}
