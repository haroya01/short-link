package com.example.short_link.post.application.write;

/**
 * Request-derived context for enriching a post view (referrer / UA / IP / UTM / source channel) —
 * the same inputs {@code ProfileVisitRecorder} takes for /u/&lt;handle&gt; visits, so the per-post
 * reader breakdown matches the profile-visit dashboard. {@link #empty()} records a bare view (no
 * dimensions) — the path used where no request context is available.
 */
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

  /** No usable visitor signal → record a bare view rather than running the classifiers. */
  public boolean isEmpty() {
    return userAgent == null && clientIp == null && referrer == null;
  }
}
