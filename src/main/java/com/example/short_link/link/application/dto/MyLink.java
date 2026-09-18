package com.example.short_link.link.application.dto;

import com.example.short_link.link.domain.ShortCode;
import java.time.Instant;
import java.util.List;

/**
 * Owner-only link data. clickCount includes bots; humanClickCount and clicksLast7d exclude them.
 */
public record MyLink(
    ShortCode shortCode,
    String originalUrl,
    Instant createdAt,
    Instant expiresAt,
    long clickCount,
    List<String> tags,
    List<Long> clicksLast7d,
    String note,
    String timezone,
    Long humanClickCount) {
  public MyLink(
      ShortCode shortCode,
      String originalUrl,
      Instant createdAt,
      Instant expiresAt,
      long clickCount,
      List<String> tags,
      List<Long> clicksLast7d) {
    this(
        shortCode,
        originalUrl,
        createdAt,
        expiresAt,
        clickCount,
        tags,
        clicksLast7d,
        null,
        "UTC",
        null);
  }
}
