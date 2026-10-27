package com.example.short_link.campaign.presentation.response;

import com.example.short_link.campaign.domain.CampaignBatchEntity;
import com.example.short_link.link.application.ShortLinkUrlBuilder;
import com.example.short_link.link.application.dto.LinkRef;
import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.domain.ShortCode;
import java.time.Instant;

public record CampaignBatchResponse(
    Long id,
    Long campaignId,
    LinkId linkId,
    ShortCode shortCode,
    String shortUrl,
    String destinationUrl,
    String name,
    String distributorName,
    String areaLabel,
    int quantity,
    String memo,
    Instant createdAt) {

  public static CampaignBatchResponse from(
      CampaignBatchEntity batch, LinkRef link, ShortLinkUrlBuilder urlBuilder) {
    return new CampaignBatchResponse(
        batch.getId(),
        batch.getCampaignId(),
        link.linkId(),
        link.shortCode(),
        urlBuilder.build(link.shortCode()),
        link.originalUrl(),
        batch.getName(),
        batch.getDistributorName(),
        batch.getAreaLabel(),
        batch.getQuantity(),
        batch.getMemo(),
        batch.getCreatedAt());
  }
}
