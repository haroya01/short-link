package com.example.short_link.admin.application.dto;

import java.time.Instant;
import java.util.Map;

/**
 * {@code totalRedirects}는 전체 클릭 수이며, 지연·오류 지표는 요청 기간의 기록으로 계산한다. 링크 정보가 없으면 클릭 수도 요청 기간 기록으로 대체한다.
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
