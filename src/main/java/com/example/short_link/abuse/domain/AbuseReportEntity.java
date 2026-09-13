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

@Entity
@Table(name = "abuse_report")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AbuseReportEntity extends BaseCreatedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** 익명 신고는 신고자 ID가 null이다. */
  @Column(name = "reporter_user_id")
  private Long reporterUserId;

  @Enumerated(EnumType.STRING)
  @Column(name = "subject_type", nullable = false, length = 16)
  private AbuseSubjectType subjectType;

  @Column(name = "subject_id", nullable = false)
  private Long subjectId;

  /** 기존 자유서술 신고에는 사유 코드가 없다. */
  @Enumerated(EnumType.STRING)
  @Column(name = "reason_code", length = 16)
  private AbuseReason reasonCode;

  /** 기존 신고 내용을 보존하기 위해 reason 컬럼을 재사용한다. */
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
