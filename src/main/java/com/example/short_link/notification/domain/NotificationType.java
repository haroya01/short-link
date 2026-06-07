package com.example.short_link.notification.domain;

/**
 * The kinds of in-app notification surfaced in the bell. The first three are "someone interacted
 * with you" off {@link com.example.short_link.common.event.BlogInteractionEvent}; the rest extend
 * that surface:
 *
 * <ul>
 *   <li>{@code SERIES_SUBSCRIBE} — someone subscribed to your series (also a BlogInteractionEvent).
 *   <li>{@code REPLY} — someone replied to your comment (off {@code CommentReplyEvent}); the
 *       recipient is the parent comment's author, distinct from the post-owner COMMENT notice.
 *   <li>{@code NEW_POST} — an author you follow published their first public post (a fan-out off
 *       {@code PostPublishedEvent}: one row per follower).
 * </ul>
 */
public enum NotificationType {
  LIKE,
  COMMENT,
  FOLLOW,
  SERIES_SUBSCRIBE,
  REPLY,
  NEW_POST
}
