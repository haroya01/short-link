package com.example.short_link.campaign.api;

import com.example.short_link.campaign.application.CampaignStatsResponse;
import com.example.short_link.campaign.application.CampaignStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/campaigns/{campaignId}/stats")
@RequiredArgsConstructor
public class CampaignStatsController {

  private final CampaignStatsService service;

  @GetMapping
  public CampaignStatsResponse get(
      @AuthenticationPrincipal Long userId, @PathVariable Long campaignId) {
    return service.statsFor(campaignId, userId);
  }
}
