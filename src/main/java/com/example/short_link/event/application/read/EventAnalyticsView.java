package com.example.short_link.event.application.read;

import java.util.List;

/**
 * 이벤트 유입 분석 — 이 기능의 차별화 핵심. "신청자 23명 — 카톡 인앱 12, 트위터 6, 직접 3" 을 만드는 데이터. channel 은 별칭 링크 라벨 우선, 없으면
 * 인앱 브라우저/referrer 로 폴백.
 */
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
