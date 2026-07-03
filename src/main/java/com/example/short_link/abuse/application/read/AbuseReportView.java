package com.example.short_link.abuse.application.read;

import com.example.short_link.abuse.domain.AbuseReportEntity;
import java.time.Instant;

public record AbuseReportView(
    Long id,
    Long reporterUserId,
    String subjectType,
    Long subjectId,
    String reason,
    String status,
    String adminNote,
    Instant createdAt,
    Instant resolvedAt,
    String subjectTitle,
    String subjectAuthorHandle,
    String subjectUrl,
    boolean subjectRemoved) {

  /** Base view with no subject snapshot — for callers that don't hydrate the reported subject. */
  public static AbuseReportView from(AbuseReportEntity report) {
    return enriched(report, null, null, null, false);
  }

  /**
   * View carrying the reported subject's snapshot — title, author handle, public URL, and whether
   * it has been taken down — so the moderation queue can show what was reported and link straight
   * to it. Snapshot fields are null/false when the subject isn't a hydratable post (USER / COMMENT,
   * or a hard-deleted post), which the client renders as the bare {@code TYPE #id} fallback.
   */
  public static AbuseReportView enriched(
      AbuseReportEntity report,
      String subjectTitle,
      String subjectAuthorHandle,
      String subjectUrl,
      boolean subjectRemoved) {
    return new AbuseReportView(
        report.getId(),
        report.getReporterUserId(),
        report.getSubjectType().name(),
        report.getSubjectId(),
        report.getReason(),
        report.getStatus().name(),
        report.getAdminNote(),
        report.getCreatedAt(),
        report.getResolvedAt(),
        subjectTitle,
        subjectAuthorHandle,
        subjectUrl,
        subjectRemoved);
  }
}
