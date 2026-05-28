package com.example.short_link.abuse.application.write;

public record ResolveAbuseReportCommand(Long reportId, Resolution resolution, String adminNote) {

  public enum Resolution {
    REVIEWING,
    RESOLVED,
    REJECTED
  }

  public ResolveAbuseReportCommand {
    if (reportId == null) throw new IllegalArgumentException("reportId required");
    if (resolution == null) throw new IllegalArgumentException("resolution required");
    if (adminNote != null && adminNote.length() > 2000) {
      throw new IllegalArgumentException("adminNote max 2000");
    }
  }
}
