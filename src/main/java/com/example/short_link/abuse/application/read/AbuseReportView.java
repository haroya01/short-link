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
    Instant resolvedAt) {

  public static AbuseReportView from(AbuseReportEntity report) {
    return new AbuseReportView(
        report.getId(),
        report.getReporterUserId(),
        report.getSubjectType().name(),
        report.getSubjectId(),
        report.getReason(),
        report.getStatus().name(),
        report.getAdminNote(),
        report.getCreatedAt(),
        report.getResolvedAt());
  }
}
