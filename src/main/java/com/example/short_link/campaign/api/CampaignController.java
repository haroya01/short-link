package com.example.short_link.campaign.api;

import com.example.short_link.campaign.application.CampaignService;
import com.example.short_link.campaign.domain.CampaignEntity;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/campaigns")
@RequiredArgsConstructor
public class CampaignController {

  private final CampaignService service;

  @PostMapping
  public ResponseEntity<CampaignDetailResponse> create(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody CampaignCreateRequest request) {
    CampaignEntity campaign = service.create(userId, request);
    return ResponseEntity.created(URI.create("/api/v1/campaigns/" + campaign.getId()))
        .body(CampaignDetailResponse.from(campaign, 0L));
  }

  @GetMapping
  public List<CampaignSummaryResponse> list(@AuthenticationPrincipal Long userId) {
    return service.list(userId).stream()
        .map(c -> CampaignSummaryResponse.from(c, service.batchCount(c.getId())))
        .toList();
  }

  @GetMapping("/{id}")
  public CampaignDetailResponse detail(
      @AuthenticationPrincipal Long userId, @PathVariable Long id) {
    CampaignEntity c = service.detail(id, userId);
    return CampaignDetailResponse.from(c, service.batchCount(c.getId()));
  }

  @PatchMapping("/{id}")
  public CampaignDetailResponse update(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long id,
      @Valid @RequestBody CampaignUpdateRequest request) {
    CampaignEntity c = service.updatePolicy(id, userId, request);
    return CampaignDetailResponse.from(c, service.batchCount(c.getId()));
  }

  @DeleteMapping("/{id}")
  public CampaignDetailResponse archive(
      @AuthenticationPrincipal Long userId, @PathVariable Long id) {
    CampaignEntity c = service.archive(id, userId);
    return CampaignDetailResponse.from(c, service.batchCount(c.getId()));
  }
}
