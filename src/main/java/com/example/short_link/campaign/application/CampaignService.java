package com.example.short_link.campaign.application;

import com.example.short_link.campaign.api.CampaignCreateRequest;
import com.example.short_link.campaign.api.CampaignUpdateRequest;
import com.example.short_link.campaign.domain.CampaignBatchRepository;
import com.example.short_link.campaign.domain.CampaignEntity;
import com.example.short_link.campaign.domain.CampaignPostEndAction;
import com.example.short_link.campaign.domain.CampaignRepository;
import com.example.short_link.campaign.domain.CampaignStatus;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CampaignService {

  private final CampaignRepository repository;
  private final CampaignBatchRepository batchRepository;

  @Transactional
  public CampaignEntity create(Long ownerId, CampaignCreateRequest request) {
    Instant now = Instant.now();
    Instant startsAt = request.startsAt() != null ? request.startsAt() : now;
    if (!request.endsAt().isAfter(startsAt)) {
      throw new InvalidCampaignPeriodException();
    }
    CampaignPostEndAction action =
        request.postEndAction() != null ? request.postEndAction() : CampaignPostEndAction.KEEP;
    if (action == CampaignPostEndAction.REDIRECT && isBlank(request.postEndDestinationUrl())) {
      throw new MissingPostEndDestinationException();
    }
    CampaignEntity campaign =
        new CampaignEntity(
            ownerId,
            request.name(),
            startsAt,
            request.endsAt(),
            request.defaultDestinationUrl(),
            action,
            request.postEndDestinationUrl());
    campaign.activateIfStarted(now);
    return repository.save(campaign);
  }

  @Transactional(readOnly = true)
  public List<CampaignEntity> list(Long ownerId) {
    return repository.findByOwnerIdOrderByCreatedAtDesc(ownerId);
  }

  @Transactional(readOnly = true)
  public CampaignEntity detail(Long id, Long ownerId) {
    CampaignEntity c = repository.findById(id).orElseThrow(CampaignNotFoundException::new);
    if (!c.isOwnedBy(ownerId)) {
      throw new CampaignNotOwnedException();
    }
    return c;
  }

  @Transactional
  public CampaignEntity updatePolicy(Long id, Long ownerId, CampaignUpdateRequest request) {
    CampaignEntity c = detail(id, ownerId);
    if (c.getStatus() == CampaignStatus.ARCHIVED) {
      throw new CampaignArchivedException();
    }
    Instant endsAt = request.endsAt() != null ? request.endsAt() : c.getEndsAt();
    if (!endsAt.isAfter(c.getStartsAt())) {
      throw new InvalidCampaignPeriodException();
    }
    CampaignPostEndAction action =
        request.postEndAction() != null ? request.postEndAction() : c.getPostEndAction();
    String postEndUrl =
        request.postEndDestinationUrl() != null
            ? request.postEndDestinationUrl()
            : c.getPostEndDestinationUrl();
    if (action == CampaignPostEndAction.REDIRECT && isBlank(postEndUrl)) {
      throw new MissingPostEndDestinationException();
    }
    String defaultDest =
        request.defaultDestinationUrl() != null
            ? request.defaultDestinationUrl()
            : c.getDefaultDestinationUrl();
    if (request.name() != null) {
      c.rename(request.name());
    }
    c.updatePolicy(endsAt, defaultDest, action, postEndUrl);
    return c;
  }

  @Transactional
  public CampaignEntity archive(Long id, Long ownerId) {
    CampaignEntity c = detail(id, ownerId);
    c.archive();
    return c;
  }

  public long batchCount(Long campaignId) {
    return batchRepository.countByCampaignId(campaignId);
  }

  /**
   * 스케줄러 진입점 — DRAFT 중 startsAt 이 도래한 것을 ACTIVE 로 전환. ACTIVE → ENDED 전환과 postEndAction 적용은 M6 에서
   * 별도로 다룬다.
   */
  @Transactional
  public int activateReady(Instant now) {
    List<CampaignEntity> ready =
        repository.findByStatusAndStartsAtLessThanEqual(CampaignStatus.DRAFT, now);
    int count = 0;
    for (CampaignEntity c : ready) {
      CampaignStatus before = c.getStatus();
      c.activateIfStarted(now);
      if (c.getStatus() != before) {
        count++;
      }
    }
    return count;
  }

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }
}
