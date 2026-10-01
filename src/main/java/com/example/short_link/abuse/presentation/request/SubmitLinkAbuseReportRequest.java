package com.example.short_link.abuse.presentation.request;

import com.example.short_link.abuse.domain.AbuseReason;
import com.example.short_link.abuse.exception.AbuseErrorCode;
import com.example.short_link.abuse.exception.AbuseException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Locale;

public record SubmitLinkAbuseReportRequest(
    @NotBlank @Size(max = 2048) String link, String reasonCode, @Size(max = 2000) String detail) {

  public AbuseReason resolvedReasonCode() {
    if (reasonCode == null || reasonCode.isBlank()) {
      throw new AbuseException(AbuseErrorCode.REASON_REQUIRED);
    }
    return AbuseReason.valueOf(reasonCode.trim().toUpperCase(Locale.ROOT));
  }
}
