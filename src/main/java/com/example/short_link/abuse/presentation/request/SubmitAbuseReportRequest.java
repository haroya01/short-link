package com.example.short_link.abuse.presentation.request;

import com.example.short_link.abuse.domain.AbuseReason;
import com.example.short_link.abuse.exception.AbuseErrorCode;
import com.example.short_link.abuse.exception.AbuseException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Locale;

// 구형 클라이언트의 자유서술 reason을 AbuseReason.OTHER와 상세로 변환한다. 신규 reasonCode/detail이 있으면 우선한다.
public record SubmitAbuseReportRequest(
    @NotBlank String subjectType,
    @NotNull Long subjectId,
    String reasonCode,
    @Size(max = 2000) String detail,
    @Size(max = 2000) String reason) {

  public AbuseReason resolvedReasonCode() {
    if (reasonCode != null && !reasonCode.isBlank()) {
      return AbuseReason.valueOf(reasonCode.trim().toUpperCase(Locale.ROOT));
    }
    if (reason != null && !reason.isBlank()) {
      return AbuseReason.OTHER;
    }
    throw new AbuseException(AbuseErrorCode.REASON_REQUIRED);
  }

  public String resolvedDetail() {
    if (detail != null && !detail.isBlank()) {
      return detail;
    }
    boolean legacyReasonPath = reasonCode == null || reasonCode.isBlank();
    if (legacyReasonPath && reason != null && !reason.isBlank()) {
      return reason;
    }
    return detail;
  }
}
