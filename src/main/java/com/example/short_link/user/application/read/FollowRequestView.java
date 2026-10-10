package com.example.short_link.user.application.read;

import com.example.short_link.user.domain.PendingFollowRequest;
import java.time.Instant;

public record FollowRequestView(
    Long userId, String username, String displayName, String avatarUrl, Instant requestedAt) {

  public static FollowRequestView of(PendingFollowRequest request) {
    return new FollowRequestView(
        request.userId(),
        request.username(),
        request.displayName(),
        request.avatarUrl(),
        request.requestedAt());
  }
}
