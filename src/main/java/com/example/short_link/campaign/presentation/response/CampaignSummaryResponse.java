package com.example.short_link.campaign.presentation.response;

import com.example.short_link.campaign.application.dto.CampaignSummary;
import com.example.short_link.campaign.domain.CampaignEntity;
import com.example.short_link.campaign.domain.CampaignStatus;
import java.time.Instant;

public record CampaignSummaryResponse(
    Long id,
    String name,
    CampaignStatus status,
    Instant startsAt,
    Instant endsAt,
    long batchCount,
    long humanClicks,
    long totalQuantity,
    Instant endedAt) {

  public static CampaignSummaryResponse from(CampaignSummary summary) {
    CampaignEntity c = summary.campaign();
    return new CampaignSummaryResponse(
        c.getId(),
        c.getName(),
        c.getStatus(),
        c.getStartsAt(),
        c.getEndsAt(),
        summary.batchCount(),
        summary.humanClicks(),
        summary.totalQuantity(),
        c.getEndedAt());
  }
}
