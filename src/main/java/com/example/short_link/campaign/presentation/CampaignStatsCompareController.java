package com.example.short_link.campaign.presentation;

import com.example.short_link.campaign.application.CampaignStatsService;
import com.example.short_link.campaign.application.dto.CampaignStatsCompareView;
import com.example.short_link.campaign.presentation.request.CampaignStatsCompareRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/campaigns/stats/compare")
@RequiredArgsConstructor
public class CampaignStatsCompareController {

  private final CampaignStatsService service;

  @PostMapping
  public CampaignStatsCompareView compare(
      @AuthenticationPrincipal Long userId,
      @Valid @RequestBody CampaignStatsCompareRequest request) {
    return service.compare(request.campaignIds(), userId);
  }
}
