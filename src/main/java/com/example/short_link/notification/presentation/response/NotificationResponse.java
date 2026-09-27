package com.example.short_link.notification.presentation.response;

import com.example.short_link.notification.application.dto.NotificationView;
import java.time.Instant;

public record NotificationResponse(
    Long id,
    String type,
    Long actorId,
    String actorUsername,
    String actorAvatarUrl,
    Long postId,
    String postSlug,
    String postTitle,
    String postAuthorUsername,
    Long seriesId,
    String seriesSlug,
    String seriesTitle,
    Long collectionId,
    String collectionName,
    boolean read,
    Instant createdAt) {

  public static NotificationResponse from(NotificationView view) {
    return new NotificationResponse(
        view.id(),
        view.type().name(),
        view.actor() == null ? null : view.actor().userId(),
        view.actor() == null ? null : view.actor().username(),
        view.actor() == null ? null : view.actor().avatarUrl(),
        postId(view),
        view.post() == null ? null : view.post().slug(),
        view.post() == null ? null : view.post().title(),
        view.post() == null ? null : view.post().authorUsername(),
        view.series() == null ? null : view.series().seriesId(),
        view.series() == null ? null : view.series().slug(),
        view.series() == null ? null : view.series().title(),
        view.collection() == null ? null : view.collection().collectionId(),
        view.collection() == null ? null : view.collection().collectionName(),
        view.read(),
        view.createdAt());
  }

  private static Long postId(NotificationView view) {
    if (view.post() != null) {
      return view.post().postId();
    }
    return view.collection() == null ? null : view.collection().postId();
  }
}
