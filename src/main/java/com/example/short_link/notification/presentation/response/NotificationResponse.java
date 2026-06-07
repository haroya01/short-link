package com.example.short_link.notification.presentation.response;

import com.example.short_link.notification.application.dto.NotificationView;
import java.time.Instant;

/**
 * One notification as the client renders it. Actor fields are flat (and null when the actor was
 * deleted, so the UI shows an anonymous label). Post fields are set for LIKE/COMMENT/REPLY/NEW_POST
 * (null otherwise); {@code postAuthorUsername} is set only when the recipient isn't the post's
 * author (REPLY / NEW_POST), so the client can build the post link. Series fields are set only for
 * SERIES_SUBSCRIBE.
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
    String postAuthorUsername,
    Long seriesId,
    String seriesSlug,
    String seriesTitle,
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
        view.post() == null ? null : view.post().authorUsername(),
        view.series() == null ? null : view.series().seriesId(),
        view.series() == null ? null : view.series().slug(),
        view.series() == null ? null : view.series().title(),
        view.read(),
        view.createdAt());
  }
}
