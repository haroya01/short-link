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

  @Column(length = 2000)
  private String reason;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private AbuseReportStatus status = AbuseReportStatus.OPEN;

  @Column(name = "resolved_at")
  private Instant resolvedAt;

  @Column(name = "admin_note", length = 2000)
  private String adminNote;

  public AbuseReportEntity(
      Long reporterUserId, AbuseSubjectType subjectType, Long subjectId, String reason) {
    this.reporterUserId = reporterUserId;
    this.subjectType = subjectType;
    this.subjectId = subjectId;
    this.reason = reason;
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
