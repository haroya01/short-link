package com.example.short_link.common.analytics;

public record VisitRequest(
    String referrer,
    String userAgent,
    String clientIp,
    String acceptLanguage,
    String sourceChannel,
    Utm utm,
    boolean gpc,
    String sessionId) {

  private static final VisitRequest EMPTY =
      new VisitRequest(null, null, null, null, null, Utm.none(), false, null);

  public static VisitRequest empty() {
    return EMPTY;
  }

  public boolean isEmpty() {
    return userAgent == null && clientIp == null && referrer == null;
  }
}
