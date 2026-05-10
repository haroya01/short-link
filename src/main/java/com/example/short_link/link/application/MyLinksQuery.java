package com.example.short_link.link.application;

import com.example.short_link.link.application.LinkFilters.ExpiryFilter;
import java.time.Instant;
import java.time.format.DateTimeParseException;

public record MyLinksQuery(
    int size,
    MyLinksCursor after,
    String q,
    String tag,
    String domain,
    ExpiryFilter expiry,
    Instant createdAfter,
    Instant createdBefore) {

  public static final int DEFAULT_SIZE = 20;
  public static final int MAX_SIZE = 100;

  public static MyLinksQuery of(
      Integer size,
      String after,
      String q,
      String tag,
      String domain,
      String expiry,
      String createdAfter,
      String createdBefore) {
    int s = size == null || size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
    return new MyLinksQuery(
        s,
        MyLinksCursor.decode(after),
        normalize(q),
        normalize(tag),
        normalize(domain),
        parseExpiry(expiry),
        parseInstant(createdAfter),
        parseInstant(createdBefore));
  }

  private static String normalize(String s) {
    if (s == null) return null;
    String trimmed = s.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private static ExpiryFilter parseExpiry(String value) {
    String normalized = normalize(value);
    if (normalized == null) return null;
    try {
      return ExpiryFilter.valueOf(normalized.toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "expiry must be one of: NEVER / ACTIVE / EXPIRED / HAS_EXPIRY / EXPIRING_SOON");
    }
  }

  private static Instant parseInstant(String value) {
    String normalized = normalize(value);
    if (normalized == null) return null;
    try {
      return Instant.parse(normalized);
    } catch (DateTimeParseException e) {
      throw new IllegalArgumentException("date must be ISO-8601 instant");
    }
  }
}
