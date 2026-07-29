package com.example.short_link.event.domain;

import com.example.short_link.common.jpa.BaseTimeEntity;
import com.example.short_link.event.exception.EventErrorCode;
import com.example.short_link.event.exception.EventException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 모집/이벤트 신청 페이지. 발행 즉시 OPEN, 참가자는 비로그인 신청. registrationCount 는 정원 판정의 단일 진실 — 증감은 반드시 repository 의
 * 조건부 원자 UPDATE 로만 한다 (엔티티 필드 조작 금지).
 */
@Entity
@Table(name = "event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventEntity extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(nullable = false, length = 16)
  private String slug;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(name = "description_md", columnDefinition = "MEDIUMTEXT")
  private String descriptionMd;

  @Column(name = "cover_image_key", length = 255)
  private String coverImageKey;

  @Column(name = "starts_at", nullable = false)
  private Instant startsAt;

  @Column(name = "ends_at")
  private Instant endsAt;

  @Column(nullable = false, length = 40)
  private String timezone;

  @Column(name = "location_text", length = 200)
  private String locationText;

  @Column(name = "location_url", length = 2048)
  private String locationUrl;

  @Column(name = "online_url", length = 2048)
  private String onlineUrl;

  @Column private Integer capacity;

  @Column(name = "close_at")
  private Instant closeAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "contact_field", nullable = false, length = 16)
  private ContactField contactField = ContactField.EMAIL;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private EventStatus status = EventStatus.OPEN;

  @Column(name = "registration_count", nullable = false)
  private int registrationCount;

  @Column(name = "primary_link_id")
  private Long primaryLinkId;

  @Column(name = "pii_purged_at")
  private Instant piiPurgedAt;

  public EventEntity(
      Long userId,
      String slug,
      String title,
      String descriptionMd,
      Instant startsAt,
      Instant endsAt,
      String timezone,
      String locationText,
      String locationUrl,
      String onlineUrl,
      Integer capacity,
      Instant closeAt,
      ContactField contactField) {
    this.userId = userId;
    this.slug = slug;
    this.title = title;
    this.descriptionMd = descriptionMd;
    this.startsAt = startsAt;
    this.endsAt = endsAt;
    this.timezone = timezone;
    this.locationText = locationText;
    this.locationUrl = locationUrl;
    this.onlineUrl = onlineUrl;
    this.capacity = capacity;
    this.closeAt = closeAt;
    this.contactField = contactField;
  }

  public boolean isOwnedBy(Long userId) {
    return this.userId.equals(userId);
  }

  public boolean acceptsRegistrations(Instant now) {
    if (status != EventStatus.OPEN) return false;
    if (closeAt != null && !now.isBefore(closeAt)) return false;
    return capacity == null || registrationCount < capacity;
  }

  public Integer spotsLeft() {
    if (capacity == null) return null;
    return Math.max(0, capacity - registrationCount);
  }

  /** PII 파기 기준 시각 — endsAt 없으면 startsAt 기준. */
  public Instant effectiveEnd() {
    return endsAt != null ? endsAt : startsAt;
  }

  public void update(
      String title,
      String descriptionMd,
      Instant startsAt,
      Instant endsAt,
      String timezone,
      String locationText,
      String locationUrl,
      String onlineUrl,
      Integer capacity,
      Instant closeAt) {
    requireNotCanceled();
    this.title = title;
    this.descriptionMd = descriptionMd;
    this.startsAt = startsAt;
    this.endsAt = endsAt;
    this.timezone = timezone;
    this.locationText = locationText;
    this.locationUrl = locationUrl;
    this.onlineUrl = onlineUrl;
    this.capacity = capacity;
    this.closeAt = closeAt;
  }

  public void updateCoverImage(String coverImageKey) {
    requireNotCanceled();
    this.coverImageKey = coverImageKey;
  }

  public void close() {
    requireNotCanceled();
    this.status = EventStatus.CLOSED;
  }

  public void reopen() {
    requireNotCanceled();
    this.status = EventStatus.OPEN;
  }

  public void cancel() {
    this.status = EventStatus.CANCELED;
  }

  public void attachPrimaryLink(Long linkId) {
    this.primaryLinkId = linkId;
  }

  public void markPiiPurged(Instant at) {
    this.piiPurgedAt = at;
  }

  private void requireNotCanceled() {
    if (status == EventStatus.CANCELED) {
      throw new EventException(EventErrorCode.EVENT_CANCELED, id);
    }
  }
}
