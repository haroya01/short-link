package com.example.short_link.post.application.read;

import java.util.List;

/**
 * Author-wide analytics overview: lifetime totals across all their posts, a windowed view-over-time
 * line aggregated across posts, and the follows their posts drove. The per-post breakdown is a
 * separate paginated surface ({@link PostPerformancePage}) so an author with hundreds of posts
 * doesn't pull them all in one response.
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
    List<DailyPoint> daily) {}
