package com.example.short_link.notification.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

// Slug and title are write-time snapshots. authorUsername, when provided, identifies the post owner
// for links opened by another recipient; otherwise the client uses recipient or actor identity.
// commentId / highlightId / milestone are omitted when null so stored payloads keep their earlier
// shape.
public record NotificationPostRef(
    Long postId,
    String slug,
    String title,
    String authorUsername,
    @JsonInclude(JsonInclude.Include.NON_NULL) Long commentId,
    @JsonInclude(JsonInclude.Include.NON_NULL) Long highlightId,
    @JsonInclude(JsonInclude.Include.NON_NULL) Integer milestone)
    implements NotificationTarget {

  public NotificationPostRef(Long postId, String slug, String title, String authorUsername) {
    this(postId, slug, title, authorUsername, null, null, null);
  }

  public NotificationPostRef(
      Long postId,
      String slug,
      String title,
      String authorUsername,
      Long commentId,
      Long highlightId) {
    this(postId, slug, title, authorUsername, commentId, highlightId, null);
  }

  @Override
  public String pushSubtitle() {
    return title;
  }
}
