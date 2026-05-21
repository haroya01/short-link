package com.example.short_link.campaign.application;

import com.example.short_link.campaign.api.CampaignBatchBulkRequest;
import com.example.short_link.campaign.api.CampaignBatchCreateRequest;
import com.example.short_link.campaign.api.CampaignBatchUpdateRequest;
import com.example.short_link.campaign.domain.CampaignBatchEntity;
import com.example.short_link.campaign.domain.CampaignBatchRepository;
import com.example.short_link.campaign.domain.CampaignEntity;
import com.example.short_link.campaign.domain.CampaignStatus;
import com.example.short_link.link.application.LinkCreated;
import com.example.short_link.link.application.LinkCreationService;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CampaignBatchService {

  private final CampaignBatchRepository batchRepository;
  private final LinkRepository linkRepository;
  private final LinkCreationService linkCreationService;
  private final CampaignService campaignService;

  @Transactional
  public BatchWithLink create(Long campaignId, Long ownerId, CampaignBatchCreateRequest request) {
    CampaignEntity campaign = campaignService.detail(campaignId, ownerId);
    rejectIfTerminal(campaign);
    String destination = resolveDestination(request.destinationUrl(), campaign);
    validateRow(request, destination, 0);
    return persistRow(campaign, ownerId, request, destination);
  }

  @Transactional
  public List<BatchWithLink> createBulk(
      Long campaignId, Long ownerId, CampaignBatchBulkRequest request) {
    CampaignEntity campaign = campaignService.detail(campaignId, ownerId);
    rejectIfTerminal(campaign);

    List<String> destinations = new ArrayList<>(request.batches().size());
    for (int i = 0; i < request.batches().size(); i++) {
      CampaignBatchCreateRequest row = request.batches().get(i);
      String destination = resolveDestination(row.destinationUrl(), campaign);
      validateRow(row, destination, i);
      destinations.add(destination);
    }

    List<BatchWithLink> out = new ArrayList<>(request.batches().size());
    for (int i = 0; i < request.batches().size(); i++) {
      out.add(persistRow(campaign, ownerId, request.batches().get(i), destinations.get(i)));
    }
    return out;
  }

  @Transactional(readOnly = true)
  public List<BatchWithLink> list(Long campaignId, Long ownerId) {
    campaignService.detail(campaignId, ownerId);
    List<CampaignBatchEntity> batches =
        batchRepository.findByCampaignIdOrderByCreatedAtAsc(campaignId);
    return batches.stream().map(this::pairWithLink).toList();
  }

  @Transactional(readOnly = true)
  public BatchWithLink detail(Long campaignId, Long batchId, Long ownerId) {
    campaignService.detail(campaignId, ownerId);
    CampaignBatchEntity batch =
        batchRepository.findById(batchId).orElseThrow(CampaignBatchNotFoundException::new);
    if (!batch.getCampaignId().equals(campaignId)) {
      throw new CampaignBatchNotFoundException();
    }
    return pairWithLink(batch);
  }

  /**
   * Metadata 만 수정 — 대표 link 와의 결합 (linkId / campaignId) 는 immutable. ENDED / ARCHIVED 캠페인은 거부. Null
   * 필드는 기존 값 유지.
   */
  @Transactional
  public BatchWithLink update(
      Long campaignId, Long batchId, Long ownerId, CampaignBatchUpdateRequest request) {
    CampaignEntity campaign = campaignService.detail(campaignId, ownerId);
    rejectIfTerminal(campaign);
    BatchWithLink current = detail(campaignId, batchId, ownerId);
    CampaignBatchEntity batch = current.batch();
    batch.editMetadata(
        request.name() != null && !request.name().isBlank() ? request.name() : batch.getName(),
        request.distributorName() != null
            ? blankToNull(request.distributorName())
            : batch.getDistributorName(),
        request.areaLabel() != null ? blankToNull(request.areaLabel()) : batch.getAreaLabel(),
        request.quantity() != null ? request.quantity() : batch.getQuantity(),
        request.memo() != null ? blankToNull(request.memo()) : batch.getMemo());
    return new BatchWithLink(batch, current.link());
  }

  /** Batch 삭제 = 대표 link 도 삭제 (인쇄된 QR 죽음). status 무관 허용 — 끝난 캠페인의 자산 정리에도 쓰임. 호출자(UI)가 경고 표시 책임. */
  @Transactional
  public void delete(Long campaignId, Long batchId, Long ownerId) {
    BatchWithLink current = detail(campaignId, batchId, ownerId);
    batchRepository.delete(current.batch());
    linkRepository.delete(current.link());
  }

  private BatchWithLink pairWithLink(CampaignBatchEntity batch) {
    LinkEntity link =
        linkRepository
            .findById(batch.getLinkId())
            .orElseThrow(() -> new IllegalStateException("orphan batch — link missing"));
    return new BatchWithLink(batch, link);
  }

  private BatchWithLink persistRow(
      CampaignEntity campaign, Long ownerId, CampaignBatchCreateRequest row, String destination) {
    LinkCreated created = linkCreationService.create(destination, ownerId, null, null);
    LinkEntity link =
        linkRepository
            .findByShortCode(created.shortCode())
            .orElseThrow(() -> new IllegalStateException("link missing right after create"));
    CampaignBatchEntity batch =
        batchRepository.save(
            new CampaignBatchEntity(
                campaign.getId(),
                link.getId(),
                row.name(),
                blankToNull(row.distributorName()),
                blankToNull(row.areaLabel()),
                row.quantity(),
                blankToNull(row.memo())));
    return new BatchWithLink(batch, link);
  }

  private static void rejectIfTerminal(CampaignEntity campaign) {
    if (campaign.getStatus() == CampaignStatus.ENDED
        || campaign.getStatus() == CampaignStatus.ARCHIVED) {
      throw new CampaignTerminalStateException();
    }
  }

  private static String resolveDestination(String rowDestination, CampaignEntity campaign) {
    if (rowDestination != null && !rowDestination.isBlank()) {
      return rowDestination.trim();
    }
    String fallback = campaign.getDefaultDestinationUrl();
    return (fallback == null || fallback.isBlank()) ? null : fallback;
  }

  private static void validateRow(CampaignBatchCreateRequest row, String destination, int index) {
    if (row.name() == null || row.name().isBlank()) {
      throw new InvalidBatchRowException(index, "name required");
    }
    if (row.quantity() <= 0) {
      throw new InvalidBatchRowException(index, "quantity must be positive");
    }
    if (destination == null) {
      throw new MissingDestinationUrlException();
    }
  }

  private static String blankToNull(String s) {
    if (s == null) return null;
    String trimmed = s.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
