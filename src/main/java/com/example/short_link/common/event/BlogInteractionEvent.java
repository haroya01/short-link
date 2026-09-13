package com.example.short_link.common.event;

import java.time.Instant;

/**
 * Published after the interaction commits. Actor names are resolved only when a matching webhook
 * exists, avoiding a lookup on the interaction path. Self-actions do not notify.
 *
 * @param recipientUserId post owner, followed user, or series owner
 * @param postId null for FOLLOW
 * @param postSlug slug snapshot; null when no post
 * @param postTitle title snapshot; null when no post
 * @param seriesId populated only for SERIES_SUBSCRIBE
 * @param seriesSlug slug snapshot; null when no series
 * @param seriesTitle title snapshot; null when no series
 */
public record BlogInteractionEvent(
    BlogInteractionType type,
    Long recipientUserId,
    Long actorUserId,
    Long postId,
    String postSlug,
    String postTitle,
    Long seriesId,
    String seriesSlug,
    String seriesTitle,
    Instant occurredAt) {

  public boolean isSelfAction() {
    return recipientUserId != null && recipientUserId.equals(actorUserId);
  }

  public static BlogInteractionEvent like(
      Long recipientUserId, Long actorUserId, Long postId, String slug, String title, Instant at) {
    return new BlogInteractionEvent(
        BlogInteractionType.LIKE,
        recipientUserId,
        actorUserId,
        postId,
        slug,
        title,
        null,
        null,
        null,
        at);
  }

  public static BlogInteractionEvent comment(
      Long recipientUserId, Long actorUserId, Long postId, String slug, String title, Instant at) {
    return new BlogInteractionEvent(
        BlogInteractionType.COMMENT,
        recipientUserId,
        actorUserId,
        postId,
        slug,
        title,
        null,
        null,
        null,
        at);
  }

  public static BlogInteractionEvent follow(Long recipientUserId, Long actorUserId, Instant at) {
    return new BlogInteractionEvent(
        BlogInteractionType.FOLLOW,
        recipientUserId,
        actorUserId,
        null,
        null,
        null,
        null,
        null,
        null,
        at);
  }

  public static BlogInteractionEvent seriesSubscribe(
      Long recipientUserId,
      Long actorUserId,
      Long seriesId,
      String seriesSlug,
      String seriesTitle,
      Instant at) {
    return new BlogInteractionEvent(
        BlogInteractionType.SERIES_SUBSCRIBE,
        recipientUserId,
        actorUserId,
        null,
        null,
        null,
        seriesId,
        seriesSlug,
        seriesTitle,
        at);
  }
}
