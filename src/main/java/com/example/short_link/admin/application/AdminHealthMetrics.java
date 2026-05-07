package com.example.short_link.admin.application;

public record AdminHealthMetrics(
    HttpLatency httpLatency,
    HttpStatusCounts httpStatusCounts,
    long rateLimitExceeded,
    long safeBrowsingMalicious,
    long authFailures,
    DbPool dbPool,
    Cache cache) {

  public record HttpLatency(
      double p50Millis, double p95Millis, double p99Millis, long sampleCount) {}

  public record HttpStatusCounts(long count2xx, long count4xx, long count5xx) {}

  public record DbPool(int active, int idle, int waiting, int max) {}

  public record Cache(long gets, long hits, long misses, double hitRatio) {}
}
