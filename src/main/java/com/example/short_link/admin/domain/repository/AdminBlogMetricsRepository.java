package com.example.short_link.admin.domain.repository;

import java.time.Instant;
import java.util.List;

public interface AdminBlogMetricsRepository {

  long totalPublishedPosts();

  /** 전체 글의 누적 조회 카운터 합계다. */
  long totalReads();

  /** Distinct authors who published, or whose post was read by a human, since {@code since}. */
  long activeAuthorsSince(Instant since);

  /** OPEN + REVIEWING abuse reports — the moderation backlog. */
  long openReportCount();

  List<TopPostRow> topPostsByReads();

  interface TopPostRow {
    Long getId();

    String getTitle();

    String getSlug();

    String getAuthorHandle();

    Long getReads();
  }
}
