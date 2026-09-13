package com.example.short_link.abuse.application.write;

import com.example.short_link.abuse.domain.AbuseReason;
import com.example.short_link.abuse.domain.AbuseSubjectType;

public record SubmitAbuseReportCommand(
    Long reporterUserId,
    AbuseSubjectType subjectType,
    Long subjectId,
    AbuseReason reasonCode,
    String detail) {

  public SubmitAbuseReportCommand {
    if (subjectType == null) throw new IllegalArgumentException("subjectType required");
    if (subjectId == null) throw new IllegalArgumentException("subjectId required");
    if (reasonCode == null) throw new IllegalArgumentException("reasonCode required");
    if (detail != null && detail.length() > 2000) {
      throw new IllegalArgumentException("detail max 2000");
    }
  }
}
