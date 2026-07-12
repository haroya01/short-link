package com.example.short_link.notification.domain;

/**
 * The kinds of in-app notification surfaced in the bell. The first three are "someone interacted
 * with you" off {@link com.example.short_link.common.event.BlogInteractionEvent}; the rest extend
 * that surface:
 *
 * <ul>
 *   <li>{@code SERIES_SUBSCRIBE} — someone subscribed to your series (also a BlogInteractionEvent).
 *   <li>{@code REPLY} — someone replied to your comment (off {@code CommentReplyEvent}) or to your
 *       reader highlight (off {@code HighlightReplyEvent}); the recipient is the thread's author,
 *       distinct from the post-owner COMMENT notice.
 *   <li>{@code NEW_POST} — an author you follow published their first public post (a fan-out off
 *       {@code PostPublishedEvent}: one row per follower).
 *   <li>{@code MENTION} — someone @-mentioned you in a comment (off {@code CommentMentionEvent}) or
 *       a highlight reply (off {@code HighlightMentionEvent}); one row per mentioned user, deduped
 *       against the COMMENT/REPLY that thread already sends.
 *   <li>{@code CONNECTED} — someone wove your post or highlight into one of their collections/paths
 *       (off {@code CollectionConnectedEvent}); the recipient is the connected work's author, so a
 *       curator connecting their own block never notifies. The target is the collection.
 *   <li>{@code PATH_GREW} — a collection/path you already have a block in gained a new block (a
 *       fan-out off the same {@code CollectionConnectedEvent}: one row per distinct prior
 *       contributor, minus the new block's author and the connecting curator). The target is the
 *       collection — "a path you're part of grew."
 * </ul>
 */
public enum NotificationType {
  LIKE,
  COMMENT,
  FOLLOW,
  SERIES_SUBSCRIBE,
  REPLY,
  NEW_POST,
  MENTION,
  CONNECTED,
  PATH_GREW
}
