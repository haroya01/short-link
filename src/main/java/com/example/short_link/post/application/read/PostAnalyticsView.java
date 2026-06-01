package com.example.short_link.post.application.read;

import java.util.List;

/**
 * Per-post analytics for the owning author: the lifetime counters shown on cards plus a windowed
 * view-over-time series (filled to a continuous daily line) and the window total.
 */
public record PostAnalyticsView(
    Long postId,
    String slug,
    String title,
    String status,
    long lifetimeViews,
    long lifetimeLikes,
    int windowDays,
    long windowViews,
    List<DailyPoint> daily) {}
