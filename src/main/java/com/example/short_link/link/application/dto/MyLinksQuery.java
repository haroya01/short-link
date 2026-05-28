package com.example.short_link.link.application.dto;

import com.example.short_link.link.domain.LinkExpiryFilter;
import java.time.Instant;
import java.time.format.DateTimeParseException;

public record MyLinksQuery(
    int size,
    MyLinksCursor after,
    String q,
    String tag,
    String domain,
    LinkExpiryFilter expiry,
    Instant createdAfter,
    Instant createdBefore,
    SortKey sort,
    SortDir dir) {

  public static final int DEFAULT_SIZE = 20;
  public static final int MAX_SIZE = 100;

  public enum SortKey {
    CREATED_AT,
    CLICK_COUNT
  }

  public enum SortDir {
    ASC,
    DESC
  }

  public static MyLinksQuery of(
      Integer size,
      String after,
      String q,
      String tag,
      String domain,
      String expiry,
      String createdAfter,
      String createdBefore) {
    return of(size, after, q, tag, domain, expiry, createdAfter, createdBefore, null, null);
  }

  public static MyLinksQuery of(
      Integer size,
      String after,
      String q,
      String tag,
      String domain,
      String expiry,
      String createdAfter,
      String createdBefore,
      String sort,
      String dir) {
    int s = size == null || size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
    return new MyLinksQuery(
        s,
        MyLinksCursor.decode(after),
        normalize(q),
        normalize(tag),
        normalize(domain),
        parseExpiry(expiry),
        parseInstant(createdAfter),
        parseInstant(createdBefore),
        parseSort(sort),
        parseDir(dir));
  }

  private static String normalize(String s) {
    if (s == null) return null;
    String trimmed = s.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private static LinkExpiryFilter parseExpiry(String value) {
    String normalized = normalize(value);
    if (normalized == null) return null;
    try {
      return LinkExpiryFilter.valueOf(normalized.toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "expiry must be one of: NEVER / ACTIVE / EXPIRED / HAS_EXPIRY / EXPIRING_SOON");
    }
  }

  private static SortKey parseSort(String value) {
    String normalized = normalize(value);
    if (normalized == null) return SortKey.CREATED_AT;
    return switch (normalized.toLowerCase()) {
      case "createdat", "created_at" -> SortKey.CREATED_AT;
      case "clickcount", "click_count" -> SortKey.CLICK_COUNT;
      default -> throw new IllegalArgumentException("sort must be one of: createdAt / clickCount");
    };
  }

  private static SortDir parseDir(String value) {
    String normalized = normalize(value);
    if (normalized == null) return SortDir.DESC;
    return switch (normalized.toLowerCase()) {
      case "asc" -> SortDir.ASC;
      case "desc" -> SortDir.DESC;
      default -> throw new IllegalArgumentException("dir must be one of: asc / desc");
    };
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
