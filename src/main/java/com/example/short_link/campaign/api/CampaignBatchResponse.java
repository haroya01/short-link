package com.example.short_link.campaign.api;

import com.example.short_link.campaign.domain.CampaignBatchEntity;
import com.example.short_link.link.application.ShortLinkUrlBuilder;
import com.example.short_link.link.domain.LinkEntity;
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

  public static CampaignBatchResponse from(
      CampaignBatchEntity batch, LinkEntity link, ShortLinkUrlBuilder urlBuilder) {
    return new CampaignBatchResponse(
        batch.getId(),
        batch.getCampaignId(),
        link.getId(),
        link.getShortCode(),
        urlBuilder.build(link.getShortCode()),
        link.getOriginalUrl(),
        batch.getName(),
        batch.getDistributorName(),
        batch.getAreaLabel(),
        batch.getQuantity(),
        batch.getMemo(),
        batch.getCreatedAt());
  }
}
