package com.example.short_link.admin.application.read;

import com.example.short_link.admin.application.dto.BlogAdminMetrics;
import com.example.short_link.admin.domain.repository.AdminBlogMetricsRepository;
import com.example.short_link.common.web.PostPublicUrlBuilder;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminBlogMetricsService {

  private static final Duration ACTIVE_WINDOW = Duration.ofDays(30);

  private final AdminBlogMetricsRepository repo;
  private final PostPublicUrlBuilder postPublicUrlBuilder;

  @Cacheable(value = "admin-overview", key = "'blog-metrics'")
  @Transactional(readOnly = true)
  public BlogAdminMetrics metrics() {
    Instant activeSince = Instant.now().minus(ACTIVE_WINDOW);
    List<BlogAdminMetrics.TopPost> topPosts =
        repo.topPostsByReads().stream()
            .map(
                r ->
                    new BlogAdminMetrics.TopPost(
                        r.getId(),
                        r.getTitle(),
                        r.getAuthorHandle(),
                        r.getReads(),
                        postPublicUrlBuilder.build(r.getAuthorHandle(), r.getSlug())))
            .toList();
    return new BlogAdminMetrics(
        repo.totalPublishedPosts(),
        repo.totalReads(),
        repo.activeAuthorsSince(activeSince),
        repo.openReportCount(),
        topPosts);
  }
}
