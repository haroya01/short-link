package com.example.short_link.campaign.presentation;

import com.example.short_link.campaign.application.CampaignStatsService;
import com.example.short_link.campaign.application.dto.CampaignStatsCompareResponse;
import com.example.short_link.campaign.presentation.request.CampaignStatsCompareRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 두 (이상의) 캠페인의 stats 를 side-by-side 비교. 단일 stats 는 별도 controller ({@link CampaignStatsController})
 * 가 처리.
 *
 * <p>POST 사용 이유 — 비교할 campaignIds 가 array body 로 들어옴. GET 으로도 가능하지만 query string 길어짐 + idempotent 라
 * POST/GET 둘 다 의미상 OK.
 */
@RestController
@RequestMapping("/api/v1/campaigns/stats/compare")
@RequiredArgsConstructor
public class CampaignStatsCompareController {

  private final CampaignStatsService service;

  @PostMapping
  public CampaignStatsCompareResponse compare(
      @AuthenticationPrincipal Long userId,
      @Valid @RequestBody CampaignStatsCompareRequest request) {
    return service.compare(request.campaignIds(), userId);
  }
}
