package com.example.short_link.admin.infrastructure.persistence;

import com.example.short_link.admin.domain.repository.AdminBlogMetricsRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class AdminBlogMetricsRepositoryAdapter implements AdminBlogMetricsRepository {

  private final JpaAdminBlogMetricsRepository jpa;

  @Override
  public long totalPublishedPosts() {
    return jpa.totalPublishedPosts();
  }

  @Override
  public long totalReads() {
    return jpa.totalReads();
  }

  @Override
  public long activeAuthorsSince(Instant since) {
    return jpa.activeAuthorsSince(since);
  }

  @Override
  public long openReportCount() {
    return jpa.openReportCount();
  }

  @Override
  public List<TopPostRow> topPostsByReads() {
    return jpa.topPostsByReads();
  }
}
