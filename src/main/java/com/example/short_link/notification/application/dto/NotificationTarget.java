package com.example.short_link.notification.application.dto;

/** The supported point-in-time targets stored in a notification payload. */
public sealed interface NotificationTarget
    permits NotificationPostRef, NotificationSeriesRef, NotificationCollectionRef {
  /** Series subscriptions intentionally have no push subtitle. */
  default String pushSubtitle() {
    return null;
  }
}
