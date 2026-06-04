package com.example.short_link.common.event;

/**
 * A reader action on an author's blog that the author can be notified about via webhook. Lives in
 * the shared kernel so the producers (post likes/comments/series, user follows) and the webhook
 * consumer both reference one enum without coupling their feature modules to each other.
 */
public enum BlogInteractionType {
  LIKE,
  COMMENT,
  FOLLOW,
  SERIES_SUBSCRIBE
}
