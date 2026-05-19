package com.example.short_link.common.observability;

import java.time.Instant;

/**
 * Immutable value handed from {@code RequestMetricsFilter} to the async writer. Keeping it separate
 * from the JPA entity means the hot-path filter can build one without any persistence imports, and
 * the buffered writer can flush a batch as a bulk insert without first promoting each to a managed
 * entity.
 */
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
