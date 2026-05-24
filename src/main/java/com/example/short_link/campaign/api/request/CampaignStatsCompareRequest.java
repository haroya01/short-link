package com.example.short_link.campaign.api.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Compare endpoint 의 body — 2~4 개의 campaign id 받음. 1개는 단일 stats endpoint 가 더 적합. */
public record CampaignStatsCompareRequest(
    @NotEmpty @Size(min = 2, max = 4) List<Long> campaignIds) {}
