package com.example.short_link.abuse.application.write;

import com.example.short_link.abuse.domain.AbuseSubjectType;

public record SubmitAbuseReportCommand(
    Long reporterUserId, AbuseSubjectType subjectType, Long subjectId, String reason) {

  public SubmitAbuseReportCommand {
    if (subjectType == null) throw new IllegalArgumentException("subjectType required");
    if (subjectId == null) throw new IllegalArgumentException("subjectId required");
    if (reason != null && reason.length() > 2000) {
      throw new IllegalArgumentException("reason max 2000");
    }
  }
}
