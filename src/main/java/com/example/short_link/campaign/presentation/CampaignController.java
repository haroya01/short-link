package com.example.short_link.campaign.presentation;

import com.example.short_link.campaign.application.read.CampaignQueryService;
import com.example.short_link.campaign.application.write.ArchiveCampaignUseCase;
import com.example.short_link.campaign.application.write.CreateCampaignUseCase;
import com.example.short_link.campaign.application.write.EndCampaignNowUseCase;
import com.example.short_link.campaign.application.write.ReapplyCampaignPolicyUseCase;
import com.example.short_link.campaign.application.write.UpdateCampaignPolicyUseCase;
import com.example.short_link.campaign.domain.CampaignEntity;
import com.example.short_link.campaign.presentation.request.CampaignCreateRequest;
import com.example.short_link.campaign.presentation.request.CampaignUpdateRequest;
import com.example.short_link.campaign.presentation.response.CampaignDetailResponse;
import com.example.short_link.campaign.presentation.response.CampaignSummaryResponse;
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

  private final CampaignQueryService query;
  private final CreateCampaignUseCase createUseCase;
  private final UpdateCampaignPolicyUseCase updateUseCase;
  private final ArchiveCampaignUseCase archiveUseCase;
  private final EndCampaignNowUseCase endNowUseCase;
  private final ReapplyCampaignPolicyUseCase reapplyUseCase;

  @PostMapping
  public ResponseEntity<CampaignDetailResponse> create(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody CampaignCreateRequest request) {
    CampaignEntity campaign = createUseCase.execute(request.toCommand(userId));
    return ResponseEntity.created(URI.create("/api/v1/campaigns/" + campaign.getId()))
        .body(CampaignDetailResponse.from(campaign, 0L));
  }

  @GetMapping
  public List<CampaignSummaryResponse> list(@AuthenticationPrincipal Long userId) {
    return query.list(userId).stream()
        .map(c -> CampaignSummaryResponse.from(c, query.batchCount(c.getId())))
        .toList();
  }

  @GetMapping("/{id}")
  public CampaignDetailResponse detail(
      @AuthenticationPrincipal Long userId, @PathVariable Long id) {
    CampaignEntity c = query.detail(id, userId);
    return CampaignDetailResponse.from(c, query.batchCount(c.getId()));
  }

  @PatchMapping("/{id}")
  public CampaignDetailResponse update(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long id,
      @Valid @RequestBody CampaignUpdateRequest request) {
    CampaignEntity c = updateUseCase.execute(request.toCommand(id, userId));
    return CampaignDetailResponse.from(c, query.batchCount(c.getId()));
  }

  @DeleteMapping("/{id}")
  public CampaignDetailResponse archive(
      @AuthenticationPrincipal Long userId, @PathVariable Long id) {
    CampaignEntity c = archiveUseCase.execute(id, userId);
    return CampaignDetailResponse.from(c, query.batchCount(c.getId()));
  }

  @PostMapping("/{id}/end")
  public CampaignDetailResponse endNow(
      @AuthenticationPrincipal Long userId, @PathVariable Long id) {
    CampaignEntity c = endNowUseCase.execute(id, userId);
    return CampaignDetailResponse.from(c, query.batchCount(c.getId()));
  }

  @PostMapping("/{id}/reapply-policy")
  public CampaignDetailResponse reapplyPolicy(
      @AuthenticationPrincipal Long userId, @PathVariable Long id) {
    CampaignEntity c = reapplyUseCase.execute(id, userId);
    return CampaignDetailResponse.from(c, query.batchCount(c.getId()));
  }
}
