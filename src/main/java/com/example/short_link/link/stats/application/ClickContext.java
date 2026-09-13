package com.example.short_link.link.stats.application;

import com.example.short_link.link.domain.LinkId;

/**
 * {@code sourceChannel}, {@code destinationId}, and {@code postId} default to null via {@link #of}.
 * {@code postId} comes from {@code ?post=} and attributes the click to the embedding post.
 */
public record ClickContext(
    LinkId linkId,
    String originalUrl,
    String referrer,
    String userAgent,
    String clientIp,
    String acceptLanguage,
    String sourceChannel,
    Long destinationId,
    Long postId,
    boolean gpc,
    String fetchSite) {

  public static ClickContext of(
      LinkId linkId,
      String originalUrl,
      String referrer,
      String userAgent,
      String clientIp,
      String acceptLanguage) {
    return new ClickContext(
        linkId,
        originalUrl,
        referrer,
        userAgent,
        clientIp,
        acceptLanguage,
        null,
        null,
        null,
        false,
        null);
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
        destinationId,
        postId,
        gpc,
        fetchSite);
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
        destinationId,
        postId,
        gpc,
        fetchSite);
  }

  public ClickContext withPostId(Long postId) {
    return new ClickContext(
        linkId,
        originalUrl,
        referrer,
        userAgent,
        clientIp,
        acceptLanguage,
        sourceChannel,
        destinationId,
        postId,
        gpc,
        fetchSite);
  }

  /// Global Privacy Control(Sec-GPC: 1) 신호 — 옵트아웃 시 재방문 식별(visitorHash)을 끈다.
  public ClickContext withGpc(boolean value) {
    return new ClickContext(
        linkId,
        originalUrl,
        referrer,
        userAgent,
        clientIp,
        acceptLanguage,
        sourceChannel,
        destinationId,
        postId,
        value,
        fetchSite);
  }

  /// Sec-Fetch-Site는 referrer 없는 클릭의 이동 맥락을 구분하며, 봇 판정에는 쓰지 않는다.
  public ClickContext withFetchSite(String value) {
    return new ClickContext(
        linkId,
        originalUrl,
        referrer,
        userAgent,
        clientIp,
        acceptLanguage,
        sourceChannel,
        destinationId,
        postId,
        gpc,
        value);
  }
}
