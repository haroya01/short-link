package com.example.short_link.campaign.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
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
public class CampaignEntity {

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

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private CampaignStatus status = CampaignStatus.DRAFT;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version private Long version;

  public CampaignEntity(
      Long ownerId,
      String name,
      Instant startsAt,
      Instant endsAt,
      String defaultDestinationUrl,
      CampaignPostEndAction postEndAction,
      String postEndDestinationUrl) {
    this.ownerId = ownerId;
    this.name = name;
    this.startsAt = startsAt;
    this.endsAt = endsAt;
    this.defaultDestinationUrl = defaultDestinationUrl;
    this.postEndAction = postEndAction == null ? CampaignPostEndAction.KEEP : postEndAction;
    this.postEndDestinationUrl = postEndDestinationUrl;
  }

  @PrePersist
  void prePersist() {
    Instant now = Instant.now();
    this.createdAt = now;
    this.updatedAt = now;
    if (this.status == null) {
      this.status = CampaignStatus.DRAFT;
    }
    if (this.postEndAction == null) {
      this.postEndAction = CampaignPostEndAction.KEEP;
    }
  }

  @PreUpdate
  void preUpdate() {
    this.updatedAt = Instant.now();
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
      String postEndDestinationUrl) {
    this.endsAt = endsAt;
    this.defaultDestinationUrl = defaultDestinationUrl;
    this.postEndAction = postEndAction == null ? CampaignPostEndAction.KEEP : postEndAction;
    this.postEndDestinationUrl = postEndDestinationUrl;
  }

  public void rename(String name) {
    this.name = name;
  }
}
