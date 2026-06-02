package com.example.short_link.campaign.application;

import com.example.short_link.campaign.application.dto.BatchWithLink;
import com.example.short_link.campaign.application.read.CampaignQueryService;
import com.example.short_link.campaign.application.write.CampaignBatchBulkCommand;
import com.example.short_link.campaign.application.write.CampaignBatchCreateCommand;
import com.example.short_link.campaign.application.write.CampaignBatchUpdateCommand;
import com.example.short_link.campaign.domain.CampaignBatchEntity;
import com.example.short_link.campaign.domain.CampaignEntity;
import com.example.short_link.campaign.domain.CampaignStatus;
import com.example.short_link.campaign.domain.repository.CampaignBatchRepository;
import com.example.short_link.campaign.exception.CampaignErrorCode;
import com.example.short_link.campaign.exception.CampaignException;
import com.example.short_link.link.application.dto.LinkCreated;
import com.example.short_link.link.application.write.CreateLinkCommand;
import com.example.short_link.link.application.write.CreateLinkUseCase;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
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
  private final CreateLinkUseCase linkCreationService;
  private final CampaignQueryService campaignQuery;

  @Transactional
  public BatchWithLink create(Long campaignId, Long ownerId, CampaignBatchCreateCommand command) {
    CampaignEntity campaign = campaignQuery.detail(campaignId, ownerId);
    rejectIfTerminal(campaign);
    String destination = resolveDestination(command.destinationUrl(), campaign);
    validateRow(command, destination, 0);
    return persistRow(campaign, ownerId, command, destination);
  }

  @Transactional
  public List<BatchWithLink> createBulk(
      Long campaignId, Long ownerId, CampaignBatchBulkCommand command) {
    CampaignEntity campaign = campaignQuery.detail(campaignId, ownerId);
    rejectIfTerminal(campaign);

    List<String> destinations = new ArrayList<>(command.batches().size());
    for (int i = 0; i < command.batches().size(); i++) {
      CampaignBatchCreateCommand row = command.batches().get(i);
      String destination = resolveDestination(row.destinationUrl(), campaign);
      validateRow(row, destination, i);
      destinations.add(destination);
    }

    List<BatchWithLink> out = new ArrayList<>(command.batches().size());
    for (int i = 0; i < command.batches().size(); i++) {
      out.add(persistRow(campaign, ownerId, command.batches().get(i), destinations.get(i)));
    }
    return out;
  }

  @Transactional(readOnly = true)
  public List<BatchWithLink> list(Long campaignId, Long ownerId) {
    campaignQuery.detail(campaignId, ownerId);
    List<CampaignBatchEntity> batches =
        batchRepository.findByCampaignIdOrderByCreatedAtAsc(campaignId);
    return batches.stream().map(this::pairWithLink).toList();
  }

  @Transactional(readOnly = true)
  public BatchWithLink detail(Long campaignId, Long batchId, Long ownerId) {
    campaignQuery.detail(campaignId, ownerId);
    CampaignBatchEntity batch =
        batchRepository
            .findById(batchId)
            .orElseThrow(() -> new CampaignException(CampaignErrorCode.CAMPAIGN_BATCH_NOT_FOUND));
    if (!batch.getCampaignId().equals(campaignId)) {
      throw new CampaignException(CampaignErrorCode.CAMPAIGN_BATCH_NOT_FOUND);
    }
    return pairWithLink(batch);
  }

  /**
   * Metadata 만 수정 — 대표 link 와의 결합 (linkId / campaignId) 는 immutable. ENDED / ARCHIVED 캠페인은 거부. Null
   * 필드는 기존 값 유지.
   */
  @Transactional
  public BatchWithLink update(
      Long campaignId, Long batchId, Long ownerId, CampaignBatchUpdateCommand command) {
    CampaignEntity campaign = campaignQuery.detail(campaignId, ownerId);
    rejectIfTerminal(campaign);
    BatchWithLink current = detail(campaignId, batchId, ownerId);
    CampaignBatchEntity batch = current.batch();
    batch.editMetadata(
        command.name() != null && !command.name().isBlank() ? command.name() : batch.getName(),
        command.distributorName() != null
            ? blankToNull(command.distributorName())
            : batch.getDistributorName(),
        command.areaLabel() != null ? blankToNull(command.areaLabel()) : batch.getAreaLabel(),
        command.quantity() != null ? command.quantity() : batch.getQuantity(),
        command.memo() != null ? blankToNull(command.memo()) : batch.getMemo());
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
            .orElseThrow(() -> new CampaignException(CampaignErrorCode.CAMPAIGN_BATCH_NOT_FOUND));
    return new BatchWithLink(batch, link);
  }

  private BatchWithLink persistRow(
      CampaignEntity campaign, Long ownerId, CampaignBatchCreateCommand row, String destination) {
    // dedup=false — 같은 destination 의 여러 batch 가 각자 다른 short code 를 갖도록 (batch:link
    // UNIQUE 제약). 인쇄물 발주 시 batch 별 추적이 가능해야 함.
    LinkCreated created =
        linkCreationService.execute(new CreateLinkCommand(destination, ownerId, null, null, false));
    LinkEntity link =
        linkRepository
            .findByShortCode(created.shortCode())
            .orElseThrow(() -> new IllegalStateException("link missing right after create"));
    CampaignBatchEntity batch =
        batchRepository.save(
            new CampaignBatchEntity(
                campaign.getId(),
                link.linkId(),
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
      throw new CampaignException(CampaignErrorCode.CAMPAIGN_TERMINAL_STATE);
    }
  }

  private static String resolveDestination(String rowDestination, CampaignEntity campaign) {
    if (rowDestination != null && !rowDestination.isBlank()) {
      return rowDestination.trim();
    }
    String fallback = campaign.getDefaultDestinationUrl();
    return (fallback == null || fallback.isBlank()) ? null : fallback;
  }

  private static void validateRow(CampaignBatchCreateCommand row, String destination, int index) {
    if (row.name() == null || row.name().isBlank()) {
      throw new CampaignException(CampaignErrorCode.INVALID_BATCH_ROW, index, "name required");
    }
    if (row.quantity() <= 0) {
      throw new CampaignException(
          CampaignErrorCode.INVALID_BATCH_ROW, index, "quantity must be positive");
    }
    if (destination == null) {
      throw new CampaignException(CampaignErrorCode.MISSING_DESTINATION_URL);
    }
  }

  private static String blankToNull(String s) {
    if (s == null) return null;
    String trimmed = s.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
