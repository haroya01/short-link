package com.example.short_link.post.presentation;

import com.example.short_link.post.application.read.AuthorAnalyticsOverview;
import com.example.short_link.post.application.read.PostAnalyticsQueryService;
import com.example.short_link.post.application.read.PostAnalyticsView;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Author-private analytics: the author dashboard overview and per-post detail. The "글이 만든 클릭"
 * differentiator (kurl link/CTA attribution) layers on later; this first cut serves the view/like
 * traction that the post_view_event log already records.
 */
@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostAnalyticsController {

  private final PostAnalyticsQueryService analytics;

  @GetMapping("/analytics/overview")
  public AuthorAnalyticsOverview overview(
      @AuthenticationPrincipal Long userId, @RequestParam(defaultValue = "30") int days) {
    return analytics.overview(userId, days);
  }

  @GetMapping("/{id}/analytics")
  public PostAnalyticsView postAnalytics(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long id,
      @RequestParam(defaultValue = "30") int days) {
    return analytics.postAnalytics(userId, id, days);
  }
}
