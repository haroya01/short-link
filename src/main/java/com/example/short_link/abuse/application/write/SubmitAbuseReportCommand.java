package com.example.short_link.abuse.application.write;

import com.example.short_link.abuse.domain.AbuseReason;
import com.example.short_link.abuse.domain.AbuseSubjectType;

/**
 * 신고 제출 커맨드. 사유는 하이브리드 — 정형 코드({@code reasonCode}) + 자유서술({@code detail}). 코드는 필수, 상세는 선택(2000자 캡).
 */
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
