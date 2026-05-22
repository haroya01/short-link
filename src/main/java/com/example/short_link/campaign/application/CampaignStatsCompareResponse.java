package com.example.short_link.campaign.application;

import com.example.short_link.campaign.api.CampaignStatsResponse;
import java.util.List;

/** 두 (이상의) 캠페인의 stats 를 side-by-side 로 묶어서 반환. 1차 vs 2차 비교, 효율 ranking 등에 사용. */
public record CampaignStatsCompareResponse(List<CampaignWithStats> campaigns) {

  public record CampaignWithStats(Long campaignId, String name, CampaignStatsResponse stats) {}
}
