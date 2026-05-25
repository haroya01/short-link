package com.example.short_link.campaign.domain;

import com.example.short_link.campaign.domain.repository.*;
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

  /**
   * postEndAction=EXPIRE 일 때 batch link 의 만료 페이지에 박힐 메시지. KEEP/REDIRECT 일 때는 저장만 되고 link 에는
   * propagate 되지 않는다 — UI 가 action 을 EXPIRE 로 바꿨다가 다시 돌릴 때 메시지가 사라지지 않도록.
   */
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

  /** startsAt 도래 → DRAFT 에서 ACTIVE 로. ENDED/ARCHIVED 는 영향 없음. */
  public void activateIfStarted(Instant now) {
    if (status == CampaignStatus.DRAFT && !startsAt.isAfter(now)) {
      this.status = CampaignStatus.ACTIVE;
    }
  }

  /** 종료 도달 또는 수동 종료. 멱등 — 이미 ENDED/ARCHIVED 면 변경 없음. */
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

  public void updatePolicy(
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
    this.name = name;
  }

  private static String normalizeMessage(String raw) {
    if (raw == null) return null;
    String trimmed = raw.trim();
    if (trimmed.isEmpty()) return null;
    return trimmed.length() > 500 ? trimmed.substring(0, 500) : trimmed;
  }
}
