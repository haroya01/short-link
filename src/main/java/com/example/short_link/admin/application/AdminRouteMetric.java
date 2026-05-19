package com.example.short_link.admin.application;

public record AdminRouteMetric(
    String uri,
    String method,
    long count,
    double p50Millis,
    double p95Millis,
    double p99Millis,
    double errorRate,
    long error5xxCount) {}
