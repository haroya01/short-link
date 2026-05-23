package com.example.short_link.campaign.api;

import com.example.short_link.campaign.domain.CampaignEntity;
import com.example.short_link.campaign.domain.CampaignPostEndAction;
import com.example.short_link.campaign.domain.CampaignStatus;
import java.time.Instant;

public record CampaignDetailResponse(
    Long id,
    String name,
    CampaignStatus status,
    Instant startsAt,
    Instant endsAt,
    Instant endedAt,
    String defaultDestinationUrl,
    CampaignPostEndAction postEndAction,
    String postEndDestinationUrl,
    String postEndMessage,
    long batchCount,
    Instant createdAt,
    Instant updatedAt) {

  public static CampaignDetailResponse from(CampaignEntity c, long batchCount) {
    return new CampaignDetailResponse(
        c.getId(),
        c.getName(),
        c.getStatus(),
        c.getStartsAt(),
        c.getEndsAt(),
        c.getEndedAt(),
        c.getDefaultDestinationUrl(),
        c.getPostEndAction(),
        c.getPostEndDestinationUrl(),
        c.getPostEndMessage(),
        batchCount,
        c.getCreatedAt(),
        c.getUpdatedAt());
  }
}
