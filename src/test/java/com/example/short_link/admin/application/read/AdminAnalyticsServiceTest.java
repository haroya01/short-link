package com.example.short_link.admin.application.read;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AdminAnalyticsServiceTest {

  @Test
  void clampsBetweenMinAndMax() {
    assertThat(AdminAnalyticsService.clamp(0, 1, 10)).isEqualTo(1);
    assertThat(AdminAnalyticsService.clamp(5, 1, 10)).isEqualTo(5);
    assertThat(AdminAnalyticsService.clamp(99, 1, 10)).isEqualTo(10);
  }

  @Test
  void rounds3DecimalPlaces() {
    assertThat(AdminAnalyticsService.round3(0.123456)).isEqualTo(0.123);
    assertThat(AdminAnalyticsService.round3(0.5)).isEqualTo(0.5);
  }

  @Test
  void formatsYearWeekIso() {
    assertThat(AdminAnalyticsService.formatYearWeek(202618)).isEqualTo("2026-W18");
    assertThat(AdminAnalyticsService.formatYearWeek(202601)).isEqualTo("2026-W01");
  }

  @Test
  void addsWeeksAcrossYearBoundary() {
    int next = AdminAnalyticsService.addWeeks(202618, 4);
    assertThat(AdminAnalyticsService.formatYearWeek(next)).isEqualTo("2026-W22");
  }
}
