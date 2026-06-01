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
    Long postId) {

  public static ClickContext of(
      LinkId linkId,
      String originalUrl,
      String referrer,
      String userAgent,
      String clientIp,
      String acceptLanguage) {
    return new ClickContext(
        linkId, originalUrl, referrer, userAgent, clientIp, acceptLanguage, null, null, null);
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
        postId);
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
        postId);
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
        postId);
  }
}
