package com.example.short_link.notification.presentation.response;

import com.example.short_link.notification.application.dto.NotificationView;
import java.time.Instant;

/**
 * One notification as the client renders it. Actor fields are flat (and null when the actor was
 * deleted, so the UI shows an anonymous label); post fields are null for postless types like
 * FOLLOW.
 */
public record NotificationResponse(
    Long id,
    String type,
    Long actorId,
    String actorUsername,
    String actorAvatarUrl,
    Long postId,
    String postSlug,
    String postTitle,
    boolean read,
    Instant createdAt) {

  public static NotificationResponse from(NotificationView view) {
    return new NotificationResponse(
        view.id(),
        view.type().name(),
        view.actor() == null ? null : view.actor().userId(),
        view.actor() == null ? null : view.actor().username(),
        view.actor() == null ? null : view.actor().avatarUrl(),
        view.post() == null ? null : view.post().postId(),
        view.post() == null ? null : view.post().slug(),
        view.post() == null ? null : view.post().title(),
        view.read(),
        view.createdAt());
  }
}
