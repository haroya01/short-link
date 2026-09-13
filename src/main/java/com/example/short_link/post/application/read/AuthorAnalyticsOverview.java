package com.example.short_link.post.application.read;

import java.util.List;

/**
 * Lifetime totals span all of the author's posts; daily views and referrers use the requested
 * window.
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
    List<ReferrerPoint> referrers) {}
