package com.example.short_link.campaign.domain;

import com.example.short_link.campaign.exception.CampaignErrorCode;
import com.example.short_link.campaign.exception.CampaignException;
import com.example.short_link.common.jpa.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "campaign")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CampaignEntity extends BaseTimeEntity {

  private static final int NAME_MAX_LENGTH = 255;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "owner_id", nullable = false)
  private Long ownerId;

  @Column(nullable = false, length = 255)
  private String name;

  @Column(name = "starts_at", nullable = false)
  private Instant startsAt;

  @Column(name = "ends_at", nullable = false)
  private Instant endsAt;

  /** 실제 ENDED 적용 시각. endsAt(예정) 과 분리 — 스케줄러 지연, 수동 종료, 연장을 다 흡수. */
  @Column(name = "ended_at")
  private Instant endedAt;

  @Column(name = "default_destination_url", length = 2048)
  private String defaultDestinationUrl;

  @Enumerated(EnumType.STRING)
  @Column(name = "post_end_action", nullable = false, length = 16)
  private CampaignPostEndAction postEndAction = CampaignPostEndAction.KEEP;

  @Column(name = "post_end_destination_url", length = 2048)
  private String postEndDestinationUrl;

  /** EXPIRE에서만 링크에 적용한다. 다른 정책에서도 보관해 EXPIRE로 돌아올 때 재사용한다. */
  @Column(name = "post_end_message", length = 500)
  private String postEndMessage;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private CampaignStatus status = CampaignStatus.DRAFT;

  @Version private Long version;

  public CampaignEntity(
      Long ownerId,
      String name,
      Instant startsAt,
      Instant endsAt,
      String defaultDestinationUrl,
      CampaignPostEndAction postEndAction,
      String postEndDestinationUrl,
      String postEndMessage) {
    validateName(name);
    validatePolicy(startsAt, endsAt, postEndAction, postEndDestinationUrl);
    this.ownerId = ownerId;
    this.name = name;
    this.startsAt = startsAt;
    this.endsAt = endsAt;
    this.defaultDestinationUrl = defaultDestinationUrl;
    this.postEndAction = postEndAction == null ? CampaignPostEndAction.KEEP : postEndAction;
    this.postEndDestinationUrl = postEndDestinationUrl;
    this.postEndMessage = normalizeMessage(postEndMessage);
  }

  public boolean isOwnedBy(Long userId) {
    return this.ownerId != null && this.ownerId.equals(userId);
  }

  public void activateIfStarted(Instant now) {
    if (status == CampaignStatus.DRAFT && !startsAt.isAfter(now)) {
      this.status = CampaignStatus.ACTIVE;
    }
  }

  public void markEnded(Instant now) {
    if (status == CampaignStatus.ENDED || status == CampaignStatus.ARCHIVED) {
      return;
    }
    this.status = CampaignStatus.ENDED;
    this.endedAt = now;
  }

  public void archive() {
    this.status = CampaignStatus.ARCHIVED;
  }

  /** 수동 종료는 보관된 캠페인에서 거부하고, 재호출 시 최초 종료 시각을 유지한다. */
  public Instant endNow(Instant now) {
    requireNotArchived();
    markEnded(now);
    return endedAt != null ? endedAt : now;
  }

  public Instant policyReapplicationTime(Instant now) {
    if (status != CampaignStatus.ENDED) {
      throw new CampaignException(CampaignErrorCode.REAPPLY_ON_NON_ENDED);
    }
    return endedAt != null ? endedAt : now;
  }

  public void requireBatchEditable() {
    if (status == CampaignStatus.ENDED || status == CampaignStatus.ARCHIVED) {
      throw new CampaignException(CampaignErrorCode.CAMPAIGN_TERMINAL_STATE);
    }
  }

  public void updatePolicy(
      Instant endsAt,
      String defaultDestinationUrl,
      CampaignPostEndAction postEndAction,
      String postEndDestinationUrl,
      String postEndMessage) {
    requireNotArchived();
    validatePolicy(startsAt, endsAt, postEndAction, postEndDestinationUrl);
    applyPolicy(
        endsAt, defaultDestinationUrl, postEndAction, postEndDestinationUrl, postEndMessage);
  }

  /** 검증 실패 시 이름만 바뀐 상태가 남지 않도록 모든 검증을 변경 전에 마친다. */
  public void updateDetails(
      String name,
      Instant endsAt,
      String defaultDestinationUrl,
      CampaignPostEndAction postEndAction,
      String postEndDestinationUrl,
      String postEndMessage) {
    requireNotArchived();
    validateName(name);
    validatePolicy(startsAt, endsAt, postEndAction, postEndDestinationUrl);
    this.name = name;
    applyPolicy(
        endsAt, defaultDestinationUrl, postEndAction, postEndDestinationUrl, postEndMessage);
  }

  private void applyPolicy(
      Instant endsAt,
      String defaultDestinationUrl,
      CampaignPostEndAction postEndAction,
      String postEndDestinationUrl,
      String postEndMessage) {
    this.endsAt = endsAt;
    this.defaultDestinationUrl = defaultDestinationUrl;
    this.postEndAction = postEndAction == null ? CampaignPostEndAction.KEEP : postEndAction;
    this.postEndDestinationUrl = postEndDestinationUrl;
    this.postEndMessage = normalizeMessage(postEndMessage);
  }

  public void rename(String name) {
    requireNotArchived();
    validateName(name);
    this.name = name;
  }

  private static void validateName(String name) {
    if (name == null || name.isBlank() || name.length() > NAME_MAX_LENGTH) {
      throw new CampaignException(CampaignErrorCode.INVALID_CAMPAIGN_NAME);
    }
  }

  private void requireNotArchived() {
    if (status == CampaignStatus.ARCHIVED) {
      throw new CampaignException(CampaignErrorCode.CAMPAIGN_ARCHIVED);
    }
  }

  private static void validatePolicy(
      Instant startsAt,
      Instant endsAt,
      CampaignPostEndAction postEndAction,
      String postEndDestinationUrl) {
    if (startsAt == null || endsAt == null || !endsAt.isAfter(startsAt)) {
      throw new CampaignException(CampaignErrorCode.INVALID_CAMPAIGN_PERIOD);
    }
    if (postEndAction == CampaignPostEndAction.REDIRECT
        && (postEndDestinationUrl == null || postEndDestinationUrl.isBlank())) {
      throw new CampaignException(CampaignErrorCode.MISSING_POST_END_DESTINATION);
    }
  }

  private static String normalizeMessage(String raw) {
    if (raw == null) return null;
    String trimmed = raw.trim();
    if (trimmed.isEmpty()) return null;
    return trimmed.length() > 500 ? trimmed.substring(0, 500) : trimmed;
  }
}
