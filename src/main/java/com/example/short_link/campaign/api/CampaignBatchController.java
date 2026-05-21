package com.example.short_link.campaign.api;

import com.example.short_link.campaign.application.BatchWithLink;
import com.example.short_link.campaign.application.CampaignBatchService;
import com.example.short_link.link.application.ShortLinkUrlBuilder;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/campaigns/{campaignId}/batches")
@RequiredArgsConstructor
public class CampaignBatchController {

  private final CampaignBatchService service;
  private final ShortLinkUrlBuilder urlBuilder;

  @PostMapping
  public ResponseEntity<CampaignBatchResponse> create(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long campaignId,
      @Valid @RequestBody CampaignBatchCreateRequest request) {
    BatchWithLink result = service.create(campaignId, userId, request);
    CampaignBatchResponse body =
        CampaignBatchResponse.from(result.batch(), result.link(), urlBuilder);
    return ResponseEntity.created(
            URI.create("/api/v1/campaigns/" + campaignId + "/batches/" + result.batch().getId()))
        .body(body);
  }

  @PostMapping("/bulk")
  public ResponseEntity<List<CampaignBatchResponse>> createBulk(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long campaignId,
      @Valid @RequestBody CampaignBatchBulkRequest request) {
    List<BatchWithLink> results = service.createBulk(campaignId, userId, request);
    List<CampaignBatchResponse> body =
        results.stream()
            .map(r -> CampaignBatchResponse.from(r.batch(), r.link(), urlBuilder))
            .toList();
    return ResponseEntity.status(201).body(body);
  }

  @GetMapping
  public List<CampaignBatchResponse> list(
      @AuthenticationPrincipal Long userId, @PathVariable Long campaignId) {
    return service.list(campaignId, userId).stream()
        .map(r -> CampaignBatchResponse.from(r.batch(), r.link(), urlBuilder))
        .toList();
  }

  @GetMapping("/{batchId}")
  public CampaignBatchResponse detail(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long campaignId,
      @PathVariable Long batchId) {
    BatchWithLink result = service.detail(campaignId, batchId, userId);
    return CampaignBatchResponse.from(result.batch(), result.link(), urlBuilder);
  }
}
