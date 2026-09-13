package com.example.short_link.campaign.application;

import com.example.short_link.campaign.application.dto.BatchWithLink;
import com.example.short_link.campaign.application.read.CampaignQueryService;
import com.example.short_link.campaign.application.write.CampaignBatchBulkCommand;
import com.example.short_link.campaign.application.write.CampaignBatchCreateCommand;
import com.example.short_link.campaign.application.write.CampaignBatchUpdateCommand;
import com.example.short_link.campaign.domain.CampaignBatchEntity;
import com.example.short_link.campaign.domain.CampaignEntity;
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
    campaign.requireBatchEditable();
    String destination = resolveDestination(command.destinationUrl(), campaign);
    validateRow(command, destination, 0);
    return persistRow(campaign, ownerId, command, destination);
  }

  @Transactional
  public List<BatchWithLink> createBulk(
      Long campaignId, Long ownerId, CampaignBatchBulkCommand command) {
    CampaignEntity campaign = campaignQuery.detail(campaignId, ownerId);
    campaign.requireBatchEditable();

    List<PreparedBatch> prepared = new ArrayList<>(command.batches().size());
    for (int i = 0; i < command.batches().size(); i++) {
      CampaignBatchCreateCommand row = command.batches().get(i);
      String destination = resolveDestination(row.destinationUrl(), campaign);
      validateRow(row, destination, i);
      prepared.add(new PreparedBatch(row, destination));
    }

    List<BatchWithLink> out = new ArrayList<>(command.batches().size());
    for (PreparedBatch batch : prepared) {
      out.add(persistRow(campaign, ownerId, batch.command(), batch.destination()));
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

  /** 대표 링크와 캠페인은 변경할 수 없다. null 필드는 유지하고 종료·보관 캠페인은 거부한다. */
  @Transactional
  public BatchWithLink update(
      Long campaignId, Long batchId, Long ownerId, CampaignBatchUpdateCommand command) {
    CampaignEntity campaign = campaignQuery.detail(campaignId, ownerId);
    campaign.requireBatchEditable();
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

  /** 대표 링크도 삭제하므로 인쇄된 QR도 무효화된다. 종료된 캠페인에서도 삭제할 수 있다. */
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
    // 배치별 추적 코드와 Batch:Link 1:1 관계를 유지하려고 URL 중복 제거를 끈다.
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

  private record PreparedBatch(CampaignBatchCreateCommand command, String destination) {}

  private static String resolveDestination(String rowDestination, CampaignEntity campaign) {
    if (rowDestination != null && !rowDestination.isBlank()) {
      return rowDestination.trim();
    }
    String fallback = campaign.getDefaultDestinationUrl();
    return (fallback == null || fallback.isBlank()) ? null : fallback;
  }

  private static void validateRow(CampaignBatchCreateCommand row, String destination, int index) {
    try {
      CampaignBatchEntity.validateMetadata(
          row.name(), row.distributorName(), row.areaLabel(), row.quantity(), row.memo());
    } catch (CampaignException e) {
      throw new CampaignException(CampaignErrorCode.INVALID_BATCH_ROW, index, e.getMessage());
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
