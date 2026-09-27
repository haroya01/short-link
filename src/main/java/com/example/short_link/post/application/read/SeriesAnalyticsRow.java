package com.example.short_link.post.application.read;

public record SeriesAnalyticsRow(
    Long seriesId,
    String slug,
    String title,
    long postCount,
    long subscriberCount,
    long totalViews,
    long totalLikes) {}
