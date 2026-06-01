package com.example.short_link.post.application.read;

import java.util.List;

/**
 * Author-wide analytics overview: lifetime totals across all their posts, a windowed view-over-time
 * line aggregated across posts, and the top posts by lifetime views.
 */
public record AuthorAnalyticsOverview(
    long totalPosts,
    long publishedPosts,
    long lifetimeViews,
    long lifetimeLikes,
    int windowDays,
    long windowViews,
    List<DailyPoint> daily,
    List<TopPostView> topPosts) {}
