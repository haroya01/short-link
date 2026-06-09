package com.example.short_link.post.application.read;

import java.time.Instant;
import java.util.List;

/**
 * One card in the global public feed — a published post plus its author summary. {@code
 * followReason} is null for the generic feeds and only set on the "following" feed, where it
 * explains why the post matched (작가/시리즈/주제).
 */
public record PublicFeedItem(
    long id,
    PublicAuthorView author,
    String slug,
    String title,
    String excerpt,
    String ogImageUrl,
    String languageTag,
    List<String> tags,
    Instant publishedAt,
    long viewCount,
    long likeCount,
    FollowReason followReason) {

  /** Build a card without a follow reason — the generic feeds (recent/trending/search/tag). */
  public PublicFeedItem(
      long id,
      PublicAuthorView author,
      String slug,
      String title,
      String excerpt,
      String ogImageUrl,
      String languageTag,
      List<String> tags,
      Instant publishedAt,
      long viewCount,
      long likeCount) {
    this(
        id,
        author,
        slug,
        title,
        excerpt,
        ogImageUrl,
        languageTag,
        tags,
        publishedAt,
        viewCount,
        likeCount,
        null);
  }

  /** A copy of this card annotated with why it surfaced in the following feed. */
  public PublicFeedItem withFollowReason(FollowReason reason) {
    return new PublicFeedItem(
        id,
        author,
        slug,
        title,
        excerpt,
        ogImageUrl,
        languageTag,
        tags,
        publishedAt,
        viewCount,
        likeCount,
        reason);
  }
}
