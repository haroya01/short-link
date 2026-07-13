package com.example.short_link.abuse.domain;

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
 * 신고 entity. spec decision #20 — 익명 / 로그인 user 둘 다 가능. CSAM auto-quarantine (PhotoDNA 같은 외부 서비스) 은
 * 별도 트랙. v0 는 단순 list + manual resolve.
 */
@Entity
@Table(name = "abuse_report")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AbuseReportEntity extends BaseCreatedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** 로그인 user 가 신고했으면 user id, 익명이면 null. captcha/PoW 게이트는 별도 트랙 (v0 미적용). */
  @Column(name = "reporter_user_id")
  private Long reporterUserId;

  @Enumerated(EnumType.STRING)
  @Column(name = "subject_type", nullable = false, length = 16)
  private AbuseSubjectType subjectType;

  @Column(name = "subject_id", nullable = false)
  private Long subjectId;

  /** 정형 사유 코드(iOS/웹 6종). legacy 신고엔 없어 null 허용. */
  @Enumerated(EnumType.STRING)
  @Column(name = "reason_code", length = 16)
  private AbuseReason reasonCode;

  /** 자유서술 상세 — 기존 free-text {@code reason} 컬럼을 그대로 재사용(데이터 이관 없음). 최대 2000자. */
  @Column(name = "reason", length = 2000)
  private String detail;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private AbuseReportStatus status = AbuseReportStatus.OPEN;

  @Column(name = "resolved_at")
  private Instant resolvedAt;

  @Column(name = "admin_note", length = 2000)
  private String adminNote;

  public AbuseReportEntity(
      Long reporterUserId,
      AbuseSubjectType subjectType,
      Long subjectId,
      AbuseReason reasonCode,
      String detail) {
    this.reporterUserId = reporterUserId;
    this.subjectType = subjectType;
    this.subjectId = subjectId;
    this.reasonCode = reasonCode;
    this.detail = detail;
    this.status = AbuseReportStatus.OPEN;
  }

  public void markReviewing(String adminNote) {
    this.status = AbuseReportStatus.REVIEWING;
    if (adminNote != null) this.adminNote = adminNote;
  }

  public void resolve(String adminNote) {
    this.status = AbuseReportStatus.RESOLVED;
    this.resolvedAt = Instant.now();
    if (adminNote != null) this.adminNote = adminNote;
  }

  public void reject(String adminNote) {
    this.status = AbuseReportStatus.REJECTED;
    this.resolvedAt = Instant.now();
    if (adminNote != null) this.adminNote = adminNote;
  }
}
