package com.example.short_link.link.application.write;

import com.example.short_link.link.domain.ShortCode;
import java.time.Instant;

public record UpdateLinkCommand(
    Long userId,
    ShortCode shortCode,
    String originalUrl,
    Instant expiresAt,
    String note,
    String expiredMessage,
    boolean clearExpiresAt) {

  public UpdateLinkCommand(
      Long userId,
      ShortCode shortCode,
      String originalUrl,
      Instant expiresAt,
      String note,
      String expiredMessage) {
    this(userId, shortCode, originalUrl, expiresAt, note, expiredMessage, false);
  }

  public UpdateLinkCommand {
    if (clearExpiresAt && expiresAt != null) {
      throw new IllegalArgumentException("expiresAt and clearExpiresAt cannot be set together");
    }
    if (userId == null) {
      throw new IllegalArgumentException("userId required");
    }
    if (shortCode == null) {
      throw new IllegalArgumentException("shortCode required");
    }
  }
}
