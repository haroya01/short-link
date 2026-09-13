package com.example.short_link.common.observability;

import java.time.Instant;

public record RequestMetric(
    Instant occurredAt,
    String route,
    String method,
    int status,
    String outcome,
    long latencyMs,
    String shortCode,
    Long userId,
    String traceId) {}
