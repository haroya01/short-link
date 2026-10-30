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
import com.example.short_link.link.application.CampaignLinks;
import com.example.short_link.link.application.dto.LinkCreated;
import com.example.short_link.link.application.dto.LinkRef;
import com.example.short_link.link.application.read.LinkRefReader;
import com.example.short_link.link.application.write.CheckedDestination;
import com.example.short_link.link.application.write.CreateLinkCommand;
import com.example.short_link.link.application.write.CreateLinkUseCase;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class CampaignBatchService {

  private final CampaignBatchRepository batchRepository;
  private final CreateLinkUseCase linkCreationService;
  private final CampaignQueryService campaignQuery;
  private final LinkRefReader links;
  private final CampaignLinks campaignLinks;
  private final TransactionTemplate transaction;

  public BatchWithLink create(Long campaignId, Long ownerId, CampaignBatchCreateCommand command) {
    CampaignEntity campaign = campaignQuery.detail(campaignId, ownerId);
    campaign.requireBatchEditable();
    String destination = resolveDestination(command.destinationUrl(), campaign);
    validateRow(command, destination, 0);
    PreparedBatch batch = checked(command, destination, ownerId);
    return transaction.execute(status -> persistRow(campaign, batch));
  }

  public List<BatchWithLink> createBulk(
      Long campaignId, Long ownerId, CampaignBatchBulkCommand command) {
    CampaignEntity campaign = campaignQuery.detail(campaignId, ownerId);
    campaign.requireBatchEditable();

    List<String> destinations = new ArrayList<>(command.batches().size());
    for (int i = 0; i < command.batches().size(); i++) {
      CampaignBatchCreateCommand row = command.batches().get(i);
      String destination = resolveDestination(row.destinationUrl(), campaign);
      validateRow(row, destination, i);
      destinations.add(destination);
    }
    List<PreparedBatch> prepared = new ArrayList<>(command.batches().size());
    for (int i = 0; i < command.batches().size(); i++) {
      prepared.add(checked(command.batches().get(i), destinations.get(i), ownerId));
    }

    return transaction.execute(
        status -> prepared.stream().map(batch -> persistRow(campaign, batch)).toList());
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

  // 대표 링크도 삭제하므로 인쇄된 QR도 무효화된다. 종료된 캠페인에서도 삭제할 수 있다.
  @Transactional
  public void delete(Long campaignId, Long batchId, Long ownerId) {
    BatchWithLink current = detail(campaignId, batchId, ownerId);
    batchRepository.delete(current.batch());
    campaignLinks.delete(current.link().linkId());
  }

  private BatchWithLink pairWithLink(CampaignBatchEntity batch) {
    LinkRef link =
        links
            .find(batch.linkId())
            .orElseThrow(() -> new CampaignException(CampaignErrorCode.CAMPAIGN_BATCH_NOT_FOUND));
    return new BatchWithLink(batch, link);
  }

  private PreparedBatch checked(CampaignBatchCreateCommand row, String destination, Long ownerId) {
    // 배치별 추적 코드와 Batch:Link 1:1 관계를 유지하려고 URL 중복 제거를 끈다.
    CreateLinkCommand link = CreateLinkCommand.perBatch(destination, ownerId);
    return new PreparedBatch(row, link, linkCreationService.check(link));
  }

  private BatchWithLink persistRow(CampaignEntity campaign, PreparedBatch batch) {
    CampaignBatchCreateCommand row = batch.row();
    LinkCreated created = linkCreationService.execute(batch.link(), batch.destination());
    LinkRef link =
        links
            .find(created.shortCode())
            .orElseThrow(() -> new IllegalStateException("link missing right after create"));
    CampaignBatchEntity saved =
        batchRepository.save(
            new CampaignBatchEntity(
                campaign.getId(),
                link.linkId(),
                row.name(),
                blankToNull(row.distributorName()),
                blankToNull(row.areaLabel()),
                row.quantity(),
                blankToNull(row.memo())));
    return new BatchWithLink(saved, link);
  }

  private record PreparedBatch(
      CampaignBatchCreateCommand row, CreateLinkCommand link, CheckedDestination destination) {}

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
