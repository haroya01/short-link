package com.example.short_link.post.domain;

import java.time.LocalDate;

/**
 * A single day's view tally from the post_view_event log — the raw, sparse output of a GROUP BY
 * DATE(viewed_at). Days with no views are absent here; the analytics service fills the gaps so the
 * dashboard can draw a continuous line.
 */
public record DailyViewCount(LocalDate date, long views) {}
