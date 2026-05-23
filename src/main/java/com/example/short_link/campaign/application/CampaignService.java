package com.example.short_link.campaign.application;

import com.example.short_link.campaign.api.CampaignCreateRequest;
import com.example.short_link.campaign.api.CampaignUpdateRequest;
import com.example.short_link.campaign.domain.CampaignBatchEntity;
import com.example.short_link.campaign.domain.CampaignBatchRepository;
import com.example.short_link.campaign.domain.CampaignEntity;
import com.example.short_link.campaign.domain.CampaignPostEndAction;
import com.example.short_link.campaign.domain.CampaignRepository;
import com.example.short_link.campaign.domain.CampaignStatus;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkRepository;
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
  private final LinkRepository linkRepository;

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
            request.postEndDestinationUrl(),
            request.postEndMessage());
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
    String postEndMessage =
        request.postEndMessage() != null ? request.postEndMessage() : c.getPostEndMessage();
    if (request.name() != null) {
      c.rename(request.name());
    }
    c.updatePolicy(endsAt, defaultDest, action, postEndUrl, postEndMessage);
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

  /** 스케줄러 진입점 — DRAFT 중 startsAt 이 도래한 것을 ACTIVE 로 전환. */
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

  /** 스케줄러 진입점 — ACTIVE 중 endsAt 이 도달한 것을 ENDED 로 전환 + postEndAction 일괄 적용. */
  @Transactional
  public int endDue(Instant now) {
    List<CampaignEntity> due =
        repository.findByStatusAndEndsAtLessThanEqual(CampaignStatus.ACTIVE, now);
    for (CampaignEntity c : due) {
      endInternal(c, now);
    }
    return due.size();
  }

  /** 수동 종료 — 운영자가 endsAt 전에 또는 후에 명시적으로 종료. ARCHIVED 거부, ENDED 멱등 (정책 재적용 효과). */
  @Transactional
  public CampaignEntity endNow(Long id, Long ownerId) {
    CampaignEntity c = detail(id, ownerId);
    if (c.getStatus() == CampaignStatus.ARCHIVED) {
      throw new CampaignArchivedException();
    }
    Instant now = Instant.now();
    if (c.getStatus() == CampaignStatus.ENDED) {
      applyPolicyToBatchLinks(c, c.getEndedAt() != null ? c.getEndedAt() : now);
      return c;
    }
    return endInternal(c, now);
  }

  /** 종료 정책 재적용 — ENDED 후 정책 변경 (postEndAction / postEndDestinationUrl) 시 명시적 액션으로 다시 박는다. */
  @Transactional
  public CampaignEntity reapplyPolicy(Long id, Long ownerId) {
    CampaignEntity c = detail(id, ownerId);
    if (c.getStatus() != CampaignStatus.ENDED) {
      throw new ReapplyOnNonEndedException();
    }
    applyPolicyToBatchLinks(c, c.getEndedAt() != null ? c.getEndedAt() : Instant.now());
    return c;
  }

  private CampaignEntity endInternal(CampaignEntity c, Instant now) {
    c.markEnded(now);
    applyPolicyToBatchLinks(c, now);
    return c;
  }

  private void applyPolicyToBatchLinks(CampaignEntity c, Instant at) {
    List<CampaignBatchEntity> batches =
        batchRepository.findByCampaignIdOrderByCreatedAtAsc(c.getId());
    for (CampaignBatchEntity batch : batches) {
      LinkEntity link = linkRepository.findById(batch.getLinkId()).orElse(null);
      if (link == null) continue;
      switch (c.getPostEndAction()) {
        case KEEP:
          // no-op — 인쇄된 QR 이 그대로 원래 destination 으로 동작
          break;
        case EXPIRE:
          link.applyCampaignExpiration(at, null, c.getPostEndMessage());
          break;
        case REDIRECT:
          link.applyCampaignExpiration(at, c.getPostEndDestinationUrl(), null);
          break;
      }
    }
  }

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }
}
