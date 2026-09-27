package com.example.short_link.event.application.read;

import java.util.List;

public record EventAnalyticsView(
    long totalClicks,
    long totalRegistrations,
    List<Bucket> clicksByLink,
    List<Bucket> clicksByClientApp,
    List<Bucket> registrationsByChannel,
    List<DailyBucket> dailyRegistrations) {

  public record Bucket(String key, long count) {}

  public record DailyBucket(String date, long count) {}
}
