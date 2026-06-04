package com.example.short_link.post.application.read;

import java.util.List;

/**
 * One series' analytics detail: its headline row plus the subscriber trend — a cumulative line of
 * how its (still-subscribed) followers accrued over the window, so the author sees growth at a
 * glance.
 *
 * @param series the series' subscriber count + member-post totals
 * @param windowDays the resolved window span (all-time spans from the first subscriber)
 * @param subscriberDaily cumulative subscriber count per day (the {@code views} field carries it)
 */
public record SeriesAnalyticsDetail(
    SeriesAnalyticsRow series, int windowDays, List<DailyPoint> subscriberDaily) {}
