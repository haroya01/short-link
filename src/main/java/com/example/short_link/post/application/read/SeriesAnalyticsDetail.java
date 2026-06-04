package com.example.short_link.post.application.read;

import java.util.List;

/**
 * One series' analytics detail: its headline row, the subscriber trend — a cumulative line of how
 * its (still-subscribed) followers accrued over the window — and per-episode performance with the
 * read-through funnel, so the author sees growth and where readers drop off at a glance.
 *
 * @param series the series' subscriber count + member-post totals
 * @param windowDays the resolved window span (all-time spans from the first subscriber)
 * @param subscriberDaily cumulative subscriber count per day (the {@code views} field carries it)
 * @param members per-episode stats in series order — performance + read-through to the next episode
 *     (lifetime, not windowed: a reader's journey through the series spans its whole life)
 */
public record SeriesAnalyticsDetail(
    SeriesAnalyticsRow series,
    int windowDays,
    List<DailyPoint> subscriberDaily,
    List<SeriesMemberStat> members) {}
