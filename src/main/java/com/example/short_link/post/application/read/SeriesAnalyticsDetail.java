package com.example.short_link.post.application.read;

import java.util.List;

/**
 * {@code subscriberDaily} accumulates still-subscribed followers within the window; its {@code
 * views} field carries subscriber counts. All-time starts at the first subscriber. {@code members}
 * use lifetime stats in series order, independent of the window.
 */
public record SeriesAnalyticsDetail(
    SeriesAnalyticsRow series,
    int windowDays,
    List<DailyPoint> subscriberDaily,
    List<SeriesMemberStat> members) {}
