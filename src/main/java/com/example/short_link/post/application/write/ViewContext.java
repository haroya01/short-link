package com.example.short_link.post.application.write;

public record ViewContext(
    String referrer,
    String userAgent,
    String clientIp,
    String acceptLanguage,
    String sourceChannel,
    String utmSource,
    String utmMedium,
    String utmCampaign,
    String utmTerm,
    String utmContent,
    boolean gpc,
    String sessionId) {

  private static final ViewContext EMPTY =
      new ViewContext(null, null, null, null, null, null, null, null, null, null, false, null);

  public static ViewContext empty() {
    return EMPTY;
  }

  public boolean isEmpty() {
    return userAgent == null && clientIp == null && referrer == null;
  }
}
