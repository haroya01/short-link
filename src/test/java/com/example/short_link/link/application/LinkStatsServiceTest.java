package com.example.short_link.link.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LinkStatsServiceTest {

  @Test
  void mapsMysqlDayOfWeekToEnumName() {
    assertThat(LinkStatsService.mapDayOfWeek(1)).isEqualTo("SUNDAY");
    assertThat(LinkStatsService.mapDayOfWeek(2)).isEqualTo("MONDAY");
    assertThat(LinkStatsService.mapDayOfWeek(3)).isEqualTo("TUESDAY");
    assertThat(LinkStatsService.mapDayOfWeek(4)).isEqualTo("WEDNESDAY");
    assertThat(LinkStatsService.mapDayOfWeek(5)).isEqualTo("THURSDAY");
    assertThat(LinkStatsService.mapDayOfWeek(6)).isEqualTo("FRIDAY");
    assertThat(LinkStatsService.mapDayOfWeek(7)).isEqualTo("SATURDAY");
  }

  @Test
  void mapsOutOfRangeDayOfWeekToUnknown() {
    assertThat(LinkStatsService.mapDayOfWeek(0)).isEqualTo("UNKNOWN");
    assertThat(LinkStatsService.mapDayOfWeek(8)).isEqualTo("UNKNOWN");
  }

  @Test
  void halfLifeNullWhenEmpty() {
    assertThat(LinkStatsService.halfLife(java.util.List.of())).isNull();
  }

  @Test
  void halfLifeIsDayWhereCumulativeReachesHalf() {
    var days =
        java.util.List.of(
            new LinkStats.DayClick(0, 30L),
            new LinkStats.DayClick(1, 20L),
            new LinkStats.DayClick(2, 50L));
    assertThat(LinkStatsService.halfLife(days)).isEqualTo(1);
  }

  @Test
  void halfLifeFirstDayWhenItAlreadyReachesHalf() {
    var days = java.util.List.of(new LinkStats.DayClick(0, 90L), new LinkStats.DayClick(7, 10L));
    assertThat(LinkStatsService.halfLife(days)).isZero();
  }

  @Test
  void halfLifeNullWhenAllZero() {
    var days = java.util.List.of(new LinkStats.DayClick(0, 0L));
    assertThat(LinkStatsService.halfLife(days)).isNull();
  }

  @Test
  void safeZoneFallsBackToDefaultForNullBlankOrInvalid() {
    assertThat(LinkStatsService.safeZone(null).getId()).isEqualTo("Asia/Seoul");
    assertThat(LinkStatsService.safeZone("").getId()).isEqualTo("Asia/Seoul");
    assertThat(LinkStatsService.safeZone("   ").getId()).isEqualTo("Asia/Seoul");
    assertThat(LinkStatsService.safeZone("Mars/Olympus").getId()).isEqualTo("Asia/Seoul");
  }

  @Test
  void safeZoneReturnsParsedZoneForValidId() {
    assertThat(LinkStatsService.safeZone("America/New_York").getId()).isEqualTo("America/New_York");
  }

  @Test
  void currentOffsetReturnsPositiveZeroForUtc() {
    assertThat(LinkStatsService.currentOffset(java.time.ZoneId.of("UTC"))).isEqualTo("+00:00");
  }

  @Test
  void currentOffsetReturnsOffsetIdForFixedZone() {
    assertThat(LinkStatsService.currentOffset(java.time.ZoneId.of("Asia/Seoul")))
        .isEqualTo("+09:00");
  }
}
