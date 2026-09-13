package com.example.short_link.link.presentation.request;

import com.example.short_link.link.application.dto.MyLinksCursor;
import com.example.short_link.link.application.dto.MyLinksQuery;
import com.example.short_link.link.application.dto.MyLinksQuery.SortDir;
import com.example.short_link.link.application.dto.MyLinksQuery.SortKey;
import com.example.short_link.link.domain.LinkExpiryFilter;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import lombok.Builder;

@Builder
public record MyLinksRequest(
    String after,
    String q,
    String tag,
    String domain,
    String expiry,
    String createdAfter,
    String createdBefore,
    String sort,
    String dir) {

  public MyLinksQuery toQuery(Integer size) {
    return MyLinksQuery.builder()
        .size(size == null ? MyLinksQuery.DEFAULT_SIZE : size)
        .after(MyLinksCursor.decode(after))
        .q(q)
        .tag(tag)
        .domain(domain)
        .expiry(parseExpiry(expiry))
        .createdAfter(parseInstant(createdAfter))
        .createdBefore(parseInstant(createdBefore))
        .sort(parseSort(sort))
        .dir(parseDir(dir))
        .build();
  }

  private static String normalize(String value) {
    if (value == null) return null;
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private static LinkExpiryFilter parseExpiry(String value) {
    String normalized = normalize(value);
    if (normalized == null) return null;
    try {
      return LinkExpiryFilter.valueOf(normalized.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "expiry must be one of: NEVER / ACTIVE / EXPIRED / HAS_EXPIRY / EXPIRING_SOON");
    }
  }

  private static SortKey parseSort(String value) {
    String normalized = normalize(value);
    if (normalized == null) return SortKey.CREATED_AT;
    return switch (normalized.toLowerCase(Locale.ROOT)) {
      case "createdat", "created_at" -> SortKey.CREATED_AT;
      case "clickcount", "click_count" -> SortKey.CLICK_COUNT;
      default -> throw new IllegalArgumentException("sort must be one of: createdAt / clickCount");
    };
  }

  private static SortDir parseDir(String value) {
    String normalized = normalize(value);
    if (normalized == null) return SortDir.DESC;
    return switch (normalized.toLowerCase(Locale.ROOT)) {
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
