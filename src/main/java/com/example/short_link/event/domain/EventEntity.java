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

/** 정원 판정에 쓰는 registrationCount는 저장소의 조건부 원자 UPDATE로만 증감한다. */
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
    validateDetails(
        title, descriptionMd, startsAt, timezone, locationText, locationUrl, onlineUrl, capacity);
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
    return registrationClosureReason(now) == null
        && (capacity == null || registrationCount < capacity);
  }

  /** 상태와 마감만 검사한다. 실제 정원 확보는 저장소의 원자 갱신이 결정한다. */
  public void requireRegistrationOpen(Instant now) {
    EventErrorCode reason = registrationClosureReason(now);
    if (reason != null) {
      throw new EventException(reason, id);
    }
  }

  private EventErrorCode registrationClosureReason(Instant now) {
    if (status == EventStatus.CANCELED) return EventErrorCode.EVENT_CANCELED;
    if (status != EventStatus.OPEN || (closeAt != null && !now.isBefore(closeAt))) {
      return EventErrorCode.EVENT_REGISTRATION_CLOSED;
    }
    return null;
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
    requireEditable();
    validateDetails(
        title, descriptionMd, startsAt, timezone, locationText, locationUrl, onlineUrl, capacity);
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
    requireEditable();
    this.coverImageKey = coverImageKey;
  }

  public void close() {
    requireEditable();
    this.status = EventStatus.CLOSED;
  }

  public void reopen() {
    requireEditable();
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

  public void requireQuestionChangesAllowed(long confirmedRegistrations) {
    requireEditable();
    if (confirmedRegistrations > 0) {
      throw new EventException(EventErrorCode.INVALID_QUESTIONS, "registrations exist");
    }
  }

  public void requireEditable() {
    if (status == EventStatus.CANCELED) {
      throw new EventException(EventErrorCode.EVENT_CANCELED, id);
    }
  }

  private static void validateDetails(
      String title,
      String descriptionMd,
      Instant startsAt,
      String timezone,
      String locationText,
      String locationUrl,
      String onlineUrl,
      Integer capacity) {
    requireText(title, 200, "title");
    requireText(timezone, 40, "timezone");
    if (startsAt == null) {
      throw new EventException(EventErrorCode.INVALID_EVENT_DETAILS, "startsAt required");
    }
    if (capacity != null && (capacity < 1 || capacity > 10000)) {
      throw new EventException(EventErrorCode.INVALID_EVENT_DETAILS, "capacity must be 1..10000");
    }
    requireMaxLength(descriptionMd, 50000, "descriptionMd");
    requireMaxLength(locationText, 200, "locationText");
    requireMaxLength(locationUrl, 2048, "locationUrl");
    requireMaxLength(onlineUrl, 2048, "onlineUrl");
  }

  private static void requireText(String value, int maxLength, String field) {
    if (value == null || value.isBlank()) {
      throw new EventException(EventErrorCode.INVALID_EVENT_DETAILS, field + " required");
    }
    requireMaxLength(value, maxLength, field);
  }

  private static void requireMaxLength(String value, int maxLength, String field) {
    if (value != null && value.length() > maxLength) {
      throw new EventException(EventErrorCode.INVALID_EVENT_DETAILS, field + " too long");
    }
  }
}
