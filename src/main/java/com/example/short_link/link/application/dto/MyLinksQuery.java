package com.example.short_link.link.application.dto;

import com.example.short_link.link.domain.LinkExpiryFilter;
import java.time.Instant;
import lombok.Builder;

/** 내 링크 조회의 정규화된 조건. HTTP 문자열 해석은 presentation에서 끝낸다. */
@Builder
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

  public MyLinksQuery {
    size = size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
    q = normalize(q);
    tag = normalize(tag);
    domain = normalize(domain);
    if (sort == null) sort = SortKey.CREATED_AT;
    if (dir == null) dir = SortDir.DESC;
  }

  public enum SortKey {
    CREATED_AT,
    CLICK_COUNT
  }

  public enum SortDir {
    ASC,
    DESC
  }

  private static String normalize(String value) {
    if (value == null) return null;
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
