package com.example.short_link.campaign.presentation.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CampaignStatsCompareRequest(
    @NotEmpty @Size(min = 2, max = 4) List<Long> campaignIds) {}
