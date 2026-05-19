package com.example.short_link.admin.application;

import java.time.Instant;
import java.util.Map;

/**
 * One row of the per-{@code shortCode} performance table. {@code totalRedirects} is the lifetime
 * click count read from the DB; latency/error fields are computed off the in-memory rolling sample
 * ring inside the requested window. {@code outcomeCounts} carries the per-outcome breakdown ({@code
 * redirect}, {@code preview}, {@code not_found}, {@code expired}, {@code view_limit}, {@code
 * blocked}, {@code password_required}, {@code error}) so the admin drill-down can show where errors
 * come from.
 */
public record AdminLinkMetric(
    String shortCode,
    String originalUrl,
    Long userId,
    String ownerEmail,
    long totalRedirects,
    long windowedRedirects,
    long p50Millis,
    long p95Millis,
    long p99Millis,
    double errorRate,
    Map<String, Long> outcomeCounts,
    Instant lastRedirectAt) {}
