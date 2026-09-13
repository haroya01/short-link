package com.example.short_link.notification.application.dto;

/**
 * Slug and title are write-time snapshots. {@code authorUsername}, when provided, identifies the
 * post owner for links opened by another recipient; otherwise the client uses recipient or actor
 * identity.
 */
public record NotificationPostRef(Long postId, String slug, String title, String authorUsername)
    implements NotificationTarget {
  @Override
  public String pushSubtitle() {
    return title;
  }
}
