package com.example.short_link.notification.domain;

/**
 * The "someone interacted with you" notifications surfaced in the in-app bell. Deliberately a
 * subset of {@link com.example.short_link.common.event.BlogInteractionType}: it omits
 * SERIES_SUBSCRIBE, which isn't part of the current notification surface, and feed-style events (a
 * followed author publishing) are a separate fan-out concern that never reaches this enum.
 */
public enum NotificationType {
  LIKE,
  COMMENT,
  FOLLOW
}
