package com.example.short_link.post.application.read;

import java.util.List;

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
