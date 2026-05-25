package com.example.short_link.campaign.presentation;

import com.example.short_link.campaign.application.CampaignBatchExportService;
import com.example.short_link.campaign.application.CampaignBatchService;
import com.example.short_link.campaign.application.dto.BatchWithLink;
import com.example.short_link.campaign.application.dto.CampaignBatchBulkRequest;
import com.example.short_link.campaign.application.dto.CampaignBatchCreateRequest;
import com.example.short_link.campaign.application.dto.CampaignBatchUpdateRequest;
import com.example.short_link.campaign.application.dto.QrOptions;
import com.example.short_link.campaign.application.helper.QrPngEncoder;
import com.example.short_link.campaign.presentation.response.CampaignBatchResponse;
import com.example.short_link.link.application.ShortLinkUrlBuilder;
import jakarta.validation.Valid;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/campaigns/{campaignId}/batches")
@RequiredArgsConstructor
public class CampaignBatchController {

  private final CampaignBatchService service;
  private final CampaignBatchExportService exportService;
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

  @PatchMapping("/{batchId}")
  public CampaignBatchResponse update(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long campaignId,
      @PathVariable Long batchId,
      @Valid @RequestBody CampaignBatchUpdateRequest request) {
    BatchWithLink result = service.update(campaignId, batchId, userId, request);
    return CampaignBatchResponse.from(result.batch(), result.link(), urlBuilder);
  }

  @DeleteMapping("/{batchId}")
  public ResponseEntity<Void> delete(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long campaignId,
      @PathVariable Long batchId) {
    service.delete(campaignId, batchId, userId);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{batchId}/qr")
  public ResponseEntity<byte[]> qrPng(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long campaignId,
      @PathVariable Long batchId,
      @RequestParam(value = "size", defaultValue = "512") int size,
      @RequestParam(value = "ec", defaultValue = "M") String ec,
      @RequestParam(value = "label", defaultValue = "false") boolean label) {
    QrOptions options = new QrOptions(size, parseEc(ec), label);
    byte[] png = exportService.exportSinglePng(campaignId, batchId, userId, options);
    return ResponseEntity.ok()
        .contentType(MediaType.IMAGE_PNG)
        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"batch-" + batchId + ".png\"")
        .body(png);
  }

  @GetMapping("/qr-zip")
  public ResponseEntity<byte[]> qrZip(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long campaignId,
      @RequestParam(value = "size", defaultValue = "512") int size,
      @RequestParam(value = "ec", defaultValue = "M") String ec,
      @RequestParam(value = "label", defaultValue = "false") boolean label) {
    QrOptions options = new QrOptions(size, parseEc(ec), label);
    byte[] zip = exportService.exportQrZip(campaignId, userId, options);
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"campaign-" + campaignId + "-qrs.zip\"")
        .body(zip);
  }

  private static QrPngEncoder.Ec parseEc(String raw) {
    try {
      return QrPngEncoder.Ec.valueOf(raw.toUpperCase());
    } catch (IllegalArgumentException e) {
      return QrPngEncoder.Ec.M;
    }
  }

  @GetMapping("/csv")
  public ResponseEntity<byte[]> csv(
      @AuthenticationPrincipal Long userId, @PathVariable Long campaignId) {
    byte[] csv = exportService.exportCsv(campaignId, userId).getBytes(StandardCharsets.UTF_8);
    return ResponseEntity.ok()
        .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"campaign-" + campaignId + "-batches.csv\"")
        .body(csv);
  }
}
