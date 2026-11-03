package com.example.short_link.notification.presentation.response;

import com.example.short_link.common.federation.CustomEmoji;
import com.example.short_link.notification.application.policy.NotificationPolicyService;
import java.time.Instant;
import java.util.List;

public record NotificationRequestResponse(
    Long actorUserId,
    Long actorRemoteId,
    String username,
    String avatarUrl,
    String profileUrl,
    long count,
    Instant lastAt,
    String displayName,
    List<CustomEmoji> emojis) {

  public static NotificationRequestResponse from(NotificationPolicyService.Request request) {
    return new NotificationRequestResponse(
        request.actor().userId(),
        request.actor().remoteId(),
        request.actor().username(),
        request.actor().avatarUrl(),
        request.actor().profileUrl(),
        request.count(),
        request.lastAt(),
        request.actor().displayName(),
        request.actor().emojis());
  }
}
