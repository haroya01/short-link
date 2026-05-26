package com.example.short_link.link.application.write;

import com.example.short_link.link.domain.ShortCode;
import java.time.Instant;

public record UpdateLinkCommand(
    Long userId,
    ShortCode shortCode,
    String originalUrl,
    Instant expiresAt,
    String note,
    String expiredMessage) {

  public UpdateLinkCommand {
    if (userId == null) {
      throw new IllegalArgumentException("userId required");
    }
    if (shortCode == null) {
      throw new IllegalArgumentException("shortCode required");
    }
  }
}
