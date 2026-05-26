package com.example.short_link.link.stats.application;

/**
 * Inputs needed to record one click. {@code sourceChannel} and {@code destinationId} are optional
 * and default to {@code null} via {@link #of}.
 */
public record ClickContext(
    Long linkId,
    String originalUrl,
    String referrer,
    String userAgent,
    String clientIp,
    String acceptLanguage,
    String sourceChannel,
    Long destinationId) {

  public static ClickContext of(
      Long linkId,
      String originalUrl,
      String referrer,
      String userAgent,
      String clientIp,
      String acceptLanguage) {
    return new ClickContext(
        linkId, originalUrl, referrer, userAgent, clientIp, acceptLanguage, null, null);
  }

  public ClickContext withSourceChannel(String sourceChannel) {
    return new ClickContext(
        linkId,
        originalUrl,
        referrer,
        userAgent,
        clientIp,
        acceptLanguage,
        sourceChannel,
        destinationId);
  }

  public ClickContext withDestination(Long destinationId) {
    return new ClickContext(
        linkId,
        originalUrl,
        referrer,
        userAgent,
        clientIp,
        acceptLanguage,
        sourceChannel,
        destinationId);
  }
}
