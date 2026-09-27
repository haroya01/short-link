package com.example.short_link.campaign.application.dto;

import java.util.List;

public record CampaignStatsCompareView(List<CampaignWithStats> campaigns) {

  public record CampaignWithStats(Long campaignId, String name, CampaignStatsView stats) {}
}
