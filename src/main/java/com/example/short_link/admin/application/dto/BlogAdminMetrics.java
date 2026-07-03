package com.example.short_link.admin.application.dto;

import java.util.List;

/**
 * Cross-author blog health for the admin console: lifetime published-post and read totals, the
 * count of authors active in the last 30 days, the unresolved-report backlog, and the most-read
 * posts. Lives under {@code admin.application} so the polymorphic Redis cache serializer ({@link
 * com.example.short_link.admin.config.AdminCacheConfig}) can round-trip it.
 */
public record BlogAdminMetrics(
    long totalPosts,
    long totalReads,
    long activeAuthors,
    long openReports,
    List<TopPost> topPosts) {

  /** {@code url} is null when the author has no handle to build a public link from. */
  public record TopPost(long id, String title, String authorHandle, long reads, String url) {}
}
