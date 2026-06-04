package com.example.short_link.post.application.read;

import java.util.List;

/**
 * Author-wide analytics overview: lifetime totals across all their posts, a windowed view-over-time
 * line aggregated across posts, the follows their posts drove, and a per-post traction table.
 */
public record AuthorAnalyticsOverview(
    long totalPosts,
    long publishedPosts,
    long lifetimeViews,
    long lifetimeLikes,
    int windowDays,
    long windowViews,
    long lifetimeLinkClicks,
    long windowLinkClicks,
    long lifetimeFollows,
    long windowFollows,
    List<DailyPoint> daily,
    List<TopPostView> topPosts) {}
