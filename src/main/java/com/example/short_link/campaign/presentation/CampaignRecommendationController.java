package com.example.short_link.campaign.presentation;

import com.example.short_link.campaign.application.CampaignRecommendationService;
import com.example.short_link.campaign.application.dto.CampaignRecommendationView;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/campaigns/{campaignId}/recommendations")
@RequiredArgsConstructor
public class CampaignRecommendationController {

  private final CampaignRecommendationService service;

  @GetMapping
  public CampaignRecommendationView recommendation(
      @AuthenticationPrincipal Long userId, @PathVariable Long campaignId) {
    return service.recommend(campaignId, userId);
  }
}
