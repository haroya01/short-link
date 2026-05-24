package com.example.short_link.campaign.api;

import com.example.short_link.campaign.application.BatchLinkInfo;
import com.example.short_link.campaign.domain.CampaignBatchEntity;
import java.time.Instant;

public record CampaignBatchResponse(
    Long id,
    Long campaignId,
    Long linkId,
    String shortCode,
    String shortUrl,
    String destinationUrl,
    String name,
    String distributorName,
    String areaLabel,
    int quantity,
    String memo,
    Instant createdAt) {

  public static CampaignBatchResponse from(CampaignBatchEntity batch, BatchLinkInfo link) {
    return new CampaignBatchResponse(
        batch.getId(),
        batch.getCampaignId(),
        link.id(),
        link.shortCode(),
        link.shortUrl(),
        link.originalUrl(),
        batch.getName(),
        batch.getDistributorName(),
        batch.getAreaLabel(),
        batch.getQuantity(),
        batch.getMemo(),
        batch.getCreatedAt());
  }
}
