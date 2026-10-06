package com.example.short_link.notification.application.dto;

public sealed interface NotificationTarget
    permits NotificationPostRef,
        NotificationSeriesRef,
        NotificationCollectionRef,
        NotificationNoteRef {
  default String pushSubtitle() {
    return null;
  }
}
