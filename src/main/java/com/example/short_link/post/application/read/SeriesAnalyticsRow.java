package com.example.short_link.post.application.read;

/**
 * One series in the author's analytics: how many subscribers it has and the traction of its member
 * posts. Series are the blog's recurring-readership unit, so subscriberCount is the headline
 * metric.
 */
public record SeriesAnalyticsRow(
    Long seriesId,
    String slug,
    String title,
    long postCount,
    long subscriberCount,
    long totalViews,
    long totalLikes) {}
