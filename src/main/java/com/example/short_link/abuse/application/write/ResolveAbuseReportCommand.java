package com.example.short_link.abuse.application.write;

import com.example.short_link.abuse.domain.ModerationAction;
import java.time.Instant;

public record ResolveAbuseReportCommand(
    Long reportId,
    Long adminUserId,
    Resolution resolution,
    ModerationAction action,
    Instant suspendUntil,
    String adminNote) {

  public enum Resolution {
    REVIEWING,
    RESOLVED,
    REJECTED
  }

  public ResolveAbuseReportCommand {
    if (reportId == null) throw new IllegalArgumentException("reportId required");
    if (resolution == null) throw new IllegalArgumentException("resolution required");
    if (action == null) action = ModerationAction.NONE;
    if (adminNote != null && adminNote.length() > 2000) {
      throw new IllegalArgumentException("adminNote max 2000");
    }
  }
}
