package com.example.short_link.event.domain;

import com.example.short_link.common.jpa.BaseCreatedEntity;
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

/** name/contact/answersJson은 이벤트 종료 30일 후 파기한다. 채널은 신청 시점의 스냅샷이며, 취소 토큰은 SHA-256 해시만 저장한다. */
@Entity
@Table(name = "event_registration")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventRegistrationEntity extends BaseCreatedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "event_id", nullable = false)
  private Long eventId;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(nullable = false, length = 254)
  private String contact;

  @Column(name = "answers_json", columnDefinition = "TEXT")
  private String answersJson;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private RegistrationStatus status = RegistrationStatus.CONFIRMED;

  @Column(name = "cancel_token_hash", nullable = false, length = 64)
  private String cancelTokenHash;

  @Column(name = "link_id")
  private Long linkId;

  @Column(name = "source_channel", length = 32)
  private String sourceChannel;

  @Column(name = "client_app", length = 32)
  private String clientApp;

  @Column(name = "referrer_host", length = 255)
  private String referrerHost;

  @Column(name = "utm_source", length = 255)
  private String utmSource;

  @Column(name = "visitor_hash", length = 64)
  private String visitorHash;

  @Column(name = "canceled_at")
  private Instant canceledAt;

  public EventRegistrationEntity(
      Long eventId, String name, String contact, String answersJson, String cancelTokenHash) {
    String normalizedName = normalizeName(name);
    this.eventId = eventId;
    this.name = normalizedName;
    this.contact = contact;
    this.answersJson = answersJson;
    this.cancelTokenHash = cancelTokenHash;
  }

  public boolean isConfirmed() {
    return status == RegistrationStatus.CONFIRMED;
  }

  public void attribute(
      Long linkId,
      String sourceChannel,
      String clientApp,
      String referrerHost,
      String utmSource,
      String visitorHash) {
    this.linkId = linkId;
    this.sourceChannel = sourceChannel;
    this.clientApp = clientApp;
    this.referrerHost = referrerHost;
    this.utmSource = utmSource;
    this.visitorHash = visitorHash;
  }

  public boolean cancel(Instant at) {
    if (!isConfirmed()) return false;
    this.status = RegistrationStatus.CANCELED;
    this.canceledAt = at;
    return true;
  }

  /** 취소 후 같은 contact 재신청 — UNIQUE(event_id, contact) 위에서 CANCELED 행을 되살린다. */
  public void reactivate(String name, String answersJson, String cancelTokenHash) {
    if (isConfirmed()) {
      throw new EventException(EventErrorCode.ALREADY_REGISTERED);
    }
    String normalizedName = normalizeName(name);
    this.name = normalizedName;
    this.answersJson = answersJson;
    this.cancelTokenHash = cancelTokenHash;
    this.status = RegistrationStatus.CONFIRMED;
    this.canceledAt = null;
  }

  public static String normalizeName(String raw) {
    if (raw == null || raw.isBlank() || raw.trim().length() > 100) {
      throw new EventException(EventErrorCode.INVALID_ANSWER, "name");
    }
    return raw.trim();
  }
}
