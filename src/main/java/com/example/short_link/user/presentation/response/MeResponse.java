package com.example.short_link.user.presentation.response;

import com.example.short_link.user.domain.UserEntity;
import java.time.Instant;

public record MeResponse(
    Long id,
    String email,
    String provider,
    String role,
    String timezone,
    Instant createdAt,
    String tier,
    Instant subscriptionCurrentPeriodEnd,
    String username) {

  public static MeResponse from(UserEntity u) {
    return new MeResponse(
        u.getId(),
        u.getEmail(),
        u.getOauthProvider(),
        u.getRole().name(),
        u.getTimezone(),
        u.getCreatedAt(),
        u.getTier().name(),
        u.getSubscriptionCurrentPeriodEnd(),
        u.getUsername());
  }
}
