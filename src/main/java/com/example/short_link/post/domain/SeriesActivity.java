package com.example.short_link.post.domain;

import java.time.Instant;

/**
 * Aggregate over a series' published member posts — how many there are and when the latest went
 * out. Ranks series for the cross-author discovery surface (most recently active first).
 */
public record SeriesActivity(Long seriesId, long postCount, Instant lastPublishedAt) {}
