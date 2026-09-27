package com.example.short_link.post.application.read;

import java.util.List;

public record SeriesAnalyticsDetail(
    SeriesAnalyticsRow series,
    int windowDays,
    List<DailyPoint> subscriberDaily,
    List<SeriesMemberStat> members) {}
