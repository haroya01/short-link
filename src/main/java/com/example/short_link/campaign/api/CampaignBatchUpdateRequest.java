package com.example.short_link.campaign.api;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Metadata 만 부분 수정. tracking link 와의 결합은 immutable — 인쇄된 QR 의 destination 보장. */
public record CampaignBatchUpdateRequest(
    @Size(max = 255) String name,
    @Size(max = 255) String distributorName,
    @Size(max = 255) String areaLabel,
    @Positive Integer quantity,
    @Size(max = 500) String memo) {}
