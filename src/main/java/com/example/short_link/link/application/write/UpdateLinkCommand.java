package com.example.short_link.link.application.write;

import java.time.Instant;

public record UpdateLinkCommand(
    Long userId,
    String shortCode,
    String originalUrl,
    Instant expiresAt,
    String note,
    String expiredMessage) {

  public UpdateLinkCommand {
    if (userId == null) {
      throw new IllegalArgumentException("userId required");
    }
    if (shortCode == null || shortCode.isBlank()) {
      throw new IllegalArgumentException("shortCode required");
    }
  }
}
