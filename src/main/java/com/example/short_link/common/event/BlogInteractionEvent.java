package com.example.short_link.common.event;

import java.time.Instant;

/**
 * Published after a blog interaction commits, so the webhook consumer can notify the recipient
 * author out of band. A shared-kernel record (not a feature event) because it's produced in the
 * post and user modules and consumed in the post.webhook module — keeping it here avoids a
 * cross-feature compile edge.
 *
 * <p>The actor's username is intentionally NOT carried: it's resolved lazily by the consumer only
 * when the recipient actually has a matching webhook, so the hot interaction path stays a single
 * insert.
 *
 * @param type interaction kind (drives which hooks fire)
 * @param recipientUserId the author who gets notified (post owner / followed user / series owner)
 * @param actorUserId who performed the action — used to skip self-notifications and resolve a name
 * @param postId the post, when relevant (LIKE/COMMENT); null for FOLLOW
 * @param postSlug post slug snapshot for the payload; null when no post
 * @param postTitle post title snapshot for the payload; null when no post
 * @param seriesId the series, for SERIES_SUBSCRIBE; null otherwise
 * @param seriesTitle series title snapshot; null otherwise
 * @param occurredAt when the interaction happened
 */
public record BlogInteractionEvent(
    BlogInteractionType type,
    Long recipientUserId,
    Long actorUserId,
    Long postId,
    String postSlug,
    String postTitle,
    Long seriesId,
    String seriesTitle,
    Instant occurredAt) {

  /** A self-action (liking your own post) shouldn't notify you. */
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
        at);
  }

  public static BlogInteractionEvent follow(Long recipientUserId, Long actorUserId, Instant at) {
    return new BlogInteractionEvent(
        BlogInteractionType.FOLLOW, recipientUserId, actorUserId, null, null, null, null, null, at);
  }

  public static BlogInteractionEvent seriesSubscribe(
      Long recipientUserId, Long actorUserId, Long seriesId, String seriesTitle, Instant at) {
    return new BlogInteractionEvent(
        BlogInteractionType.SERIES_SUBSCRIBE,
        recipientUserId,
        actorUserId,
        null,
        null,
        null,
        seriesId,
        seriesTitle,
        at);
  }
}
