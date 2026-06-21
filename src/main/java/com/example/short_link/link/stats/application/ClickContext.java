package com.example.short_link.link.stats.application;

import com.example.short_link.link.domain.LinkId;

/**
 * Inputs needed to record one click. {@code sourceChannel}, {@code destinationId} and {@code
 * postId} are optional and default to {@code null} via {@link #of}. {@code postId} attributes a
 * click to the blog post that embedded the link ("이 글이 만든 클릭") — set when the redirect carries
 * {@code ?post=}.
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
    boolean gpc) {

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
        false);
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
        gpc);
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
        gpc);
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
        gpc);
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
        value);
  }
}
