package com.example.short_link.post.application.read;

import com.example.short_link.post.domain.PostLinkClick;
import java.util.List;

public record PostAnalyticsView(
    Long postId,
    String slug,
    String title,
    String status,
    long lifetimeViews,
    long lifetimeLikes,
    int windowDays,
    long windowViews,
    long lifetimeLinkClicks,
    long windowLinkClicks,
    long lifetimeFollows,
    long windowFollows,
    List<DailyPoint> daily,
    List<PostLinkClick> linkBreakdown) {}
