package com.example.short_link.notification.presentation.response;

import com.example.short_link.notification.application.policy.NotificationPolicyService;
import java.time.Instant;

public record NotificationRequestResponse(
    Long actorUserId,
    Long actorRemoteId,
    String username,
    String avatarUrl,
    String profileUrl,
    long count,
    Instant lastAt) {

  public static NotificationRequestResponse from(NotificationPolicyService.Request request) {
    return new NotificationRequestResponse(
        request.actor().userId(),
        request.actor().remoteId(),
        request.actor().username(),
        request.actor().avatarUrl(),
        request.actor().profileUrl(),
        request.count(),
        request.lastAt());
  }
}
