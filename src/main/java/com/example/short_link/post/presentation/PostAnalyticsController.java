package com.example.short_link.post.presentation;

import com.example.short_link.post.application.read.AuthorAnalyticsOverview;
import com.example.short_link.post.application.read.PostAnalyticsQueryService;
import com.example.short_link.post.application.read.PostAnalyticsView;
import com.example.short_link.post.application.read.PostPerformanceResult;
import com.example.short_link.post.application.read.PostReadStats;
import com.example.short_link.post.application.read.PostReadStatsService;
import com.example.short_link.post.application.read.SeriesAnalyticsDetail;
import com.example.short_link.post.application.read.SeriesAnalyticsRow;
import java.util.List;
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
  private final PostReadStatsService readStats;

  @GetMapping("/analytics/overview")
  public AuthorAnalyticsOverview overview(
      @AuthenticationPrincipal Long userId, @RequestParam(defaultValue = "30") int days) {
    return analytics.overview(userId, days);
  }

  /**
   * Paginated per-post performance table (views·likes·follows) — infinite scroll. {@code sort} is
   * one of views|likes|recent (unknown falls back to views).
   */
  @GetMapping("/analytics/posts")
  public PostPerformanceResult performance(
      @AuthenticationPrincipal Long userId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "views") String sort) {
    return analytics.postPerformance(
        userId, page, size, com.example.short_link.post.domain.PostPerformanceSort.fromParam(sort));
  }

  /** Per-series analytics — subscriber count + member-post traction, newest series first. */
  @GetMapping("/analytics/series")
  public List<SeriesAnalyticsRow> seriesAnalytics(@AuthenticationPrincipal Long userId) {
    return analytics.seriesAnalytics(userId);
  }

  /** One series' detail — headline metrics + cumulative subscriber trend over the window. */
  @GetMapping("/analytics/series/{id}")
  public SeriesAnalyticsDetail seriesDetail(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long id,
      @RequestParam(defaultValue = "30") int days) {
    return analytics.seriesDetail(userId, id, days);
  }

  @GetMapping("/{id}/analytics")
  public PostAnalyticsView postAnalytics(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long id,
      @RequestParam(defaultValue = "30") int days) {
    return analytics.postAnalytics(userId, id, days);
  }

  /** Deep reader breakdown for one post (누가·어디서 봤나) — same shape as the profile-visit dashboard. */
  @GetMapping("/{id}/stats")
  public PostReadStats postStats(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
    return readStats.forPost(userId, id);
  }
}
