package com.example.short_link.event.application.read;

import java.util.List;

/** 채널은 별칭 링크 라벨을 우선 사용하고, 없으면 인앱 브라우저/referrer로 폴백한다. */
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
