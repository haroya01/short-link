package com.example.short_link.admin.application;

import java.util.Map;

/**
 * One row of the route-level performance table. {@code statusDistribution} carries the count of
 * samples per HTTP status code so the admin can see at a glance how a route's 200/4xx/5xx mix looks
 * — useful when a spike in {@code count} is actually a 404 storm.
 */
public record AdminRouteMetric(
    String uri,
    String method,
    long count,
    double p50Millis,
    double p95Millis,
    double p99Millis,
    double errorRate,
    long error5xxCount,
    Map<String, Long> statusDistribution) {}
