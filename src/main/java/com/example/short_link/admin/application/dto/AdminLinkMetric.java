package com.example.short_link.admin.application.dto;

import java.time.Instant;
import java.util.Map;

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
