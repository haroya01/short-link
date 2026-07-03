package com.example.short_link.admin.domain.repository;

import java.time.Instant;
import java.util.List;

/** Cross-author blog aggregates for the admin console. */
public interface AdminBlogMetricsRepository {

  long totalPublishedPosts();

  /** Lifetime reads (sum of the denormalized per-post view counter) across all posts. */
  long totalReads();

  /** Distinct authors who published, or whose post was read by a human, since {@code since}. */
  long activeAuthorsSince(Instant since);

  /** OPEN + REVIEWING abuse reports — the moderation backlog. */
  long openReportCount();

  /** The most-read published posts, highest first. */
  List<TopPostRow> topPostsByReads();

  interface TopPostRow {
    Long getId();

    String getTitle();

    String getSlug();

    String getAuthorHandle();

    Long getReads();
  }
}
