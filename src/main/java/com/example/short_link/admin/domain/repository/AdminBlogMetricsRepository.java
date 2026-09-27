package com.example.short_link.admin.domain.repository;

import java.time.Instant;
import java.util.List;

public interface AdminBlogMetricsRepository {

  long totalPublishedPosts();

  long totalReads();

  long activeAuthorsSince(Instant since);

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
