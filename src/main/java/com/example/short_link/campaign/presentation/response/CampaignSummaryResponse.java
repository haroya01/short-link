package com.example.short_link.campaign.presentation.response;

import com.example.short_link.campaign.domain.CampaignEntity;
import com.example.short_link.campaign.domain.CampaignStatus;
import java.time.Instant;

public record CampaignSummaryResponse(
    Long id,
    String name,
    CampaignStatus status,
    Instant startsAt,
    Instant endsAt,
    long batchCount) {

  public static CampaignSummaryResponse from(CampaignEntity c, long batchCount) {
    return new CampaignSummaryResponse(
        c.getId(), c.getName(), c.getStatus(), c.getStartsAt(), c.getEndsAt(), batchCount);
  }
}
