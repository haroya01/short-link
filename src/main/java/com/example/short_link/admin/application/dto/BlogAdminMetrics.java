package com.example.short_link.admin.application.dto;

import java.util.List;

/**
 * {@link com.example.short_link.admin.config.AdminCacheConfig}의 역직렬화 허용 범위인 {@code
 * admin.application} 패키지에 둔다.
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
