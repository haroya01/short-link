package com.example.short_link.event.domain;

import com.example.short_link.common.jpa.BaseCreatedEntity;
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
 * 비로그인 신청 1건. name/contact/answersJson 은 PII — 이벤트 종료 30일 후 스케줄러가 파기. 채널 스냅샷 컬럼은 신청 시점에 클릭 이벤트에서
 * 비정규화해 박는다 (대시보드가 click_event 조인 없이 집계). 취소는 cancelTokenHash (SHA-256) 로만 — 원본 토큰은 확인 메일/완료 화면에만
 * 존재.
 */
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
    this.eventId = eventId;
    this.name = name;
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

  public void cancel(Instant at) {
    this.status = RegistrationStatus.CANCELED;
    this.canceledAt = at;
  }

  /** 취소 후 같은 contact 재신청 — UNIQUE(event_id, contact) 위에서 CANCELED 행을 되살린다. */
  public void reactivate(String name, String answersJson, String cancelTokenHash) {
    this.name = name;
    this.answersJson = answersJson;
    this.cancelTokenHash = cancelTokenHash;
    this.status = RegistrationStatus.CONFIRMED;
    this.canceledAt = null;
  }

  public void purgePii() {
    this.name = "[purged]";
    this.contact = "purged:" + id;
    this.answersJson = null;
  }
}
