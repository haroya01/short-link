package com.example.short_link.abuse.application.read;

import com.example.short_link.abuse.domain.AbuseReportEntity;
import java.time.Instant;

/** 대상을 찾지 못하면 스냅샷 필드는 null/false로 반환한다. */
public record AbuseReportView(
    Long id,
    Long reporterUserId,
    String subjectType,
    Long subjectId,
    String reasonCode,
    String detail,
    String status,
    String adminNote,
    Instant createdAt,
    Instant resolvedAt,
    String subjectTitle,
    String subjectAuthorHandle,
    String subjectUrl,
    String subjectExcerpt,
    boolean subjectRemoved) {

  public record SubjectSnapshot(
      String title, String authorHandle, String url, String excerpt, boolean removed) {

    public static final SubjectSnapshot EMPTY = new SubjectSnapshot(null, null, null, null, false);
  }

  public static AbuseReportView from(AbuseReportEntity report) {
    return of(report, SubjectSnapshot.EMPTY);
  }

  public static AbuseReportView of(AbuseReportEntity report, SubjectSnapshot snapshot) {
    return new AbuseReportView(
        report.getId(),
        report.getReporterUserId(),
        report.getSubjectType().name(),
        report.getSubjectId(),
        report.getReasonCode() == null ? null : report.getReasonCode().name(),
        report.getDetail(),
        report.getStatus().name(),
        report.getAdminNote(),
        report.getCreatedAt(),
        report.getResolvedAt(),
        snapshot.title(),
        snapshot.authorHandle(),
        snapshot.url(),
        snapshot.excerpt(),
        snapshot.removed());
  }
}
