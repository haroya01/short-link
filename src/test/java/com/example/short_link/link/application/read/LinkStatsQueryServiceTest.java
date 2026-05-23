package com.example.short_link.link.application.read;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.link.application.LinkStats;
import org.junit.jupiter.api.Test;

class LinkStatsQueryServiceTest {

  @Test
  void mapsMysqlDayOfWeekToEnumName() {
    assertThat(LinkStatsQueryService.mapDayOfWeek(1)).isEqualTo("SUNDAY");
    assertThat(LinkStatsQueryService.mapDayOfWeek(2)).isEqualTo("MONDAY");
    assertThat(LinkStatsQueryService.mapDayOfWeek(3)).isEqualTo("TUESDAY");
    assertThat(LinkStatsQueryService.mapDayOfWeek(4)).isEqualTo("WEDNESDAY");
    assertThat(LinkStatsQueryService.mapDayOfWeek(5)).isEqualTo("THURSDAY");
    assertThat(LinkStatsQueryService.mapDayOfWeek(6)).isEqualTo("FRIDAY");
    assertThat(LinkStatsQueryService.mapDayOfWeek(7)).isEqualTo("SATURDAY");
  }

  @Test
  void mapsOutOfRangeDayOfWeekToUnknown() {
    assertThat(LinkStatsQueryService.mapDayOfWeek(0)).isEqualTo("UNKNOWN");
    assertThat(LinkStatsQueryService.mapDayOfWeek(8)).isEqualTo("UNKNOWN");
  }

  @Test
  void halfLifeNullWhenEmpty() {
    assertThat(LinkStatsQueryService.halfLife(java.util.List.of())).isNull();
  }

  @Test
  void halfLifeIsDayWhereCumulativeReachesHalf() {
    var days =
        java.util.List.of(
            new LinkStats.DayClick(0, 30L),
            new LinkStats.DayClick(1, 20L),
            new LinkStats.DayClick(2, 50L));
    assertThat(LinkStatsQueryService.halfLife(days)).isEqualTo(1);
  }

  @Test
  void halfLifeFirstDayWhenItAlreadyReachesHalf() {
    var days = java.util.List.of(new LinkStats.DayClick(0, 90L), new LinkStats.DayClick(7, 10L));
    assertThat(LinkStatsQueryService.halfLife(days)).isZero();
  }

  @Test
  void halfLifeNullWhenAllZero() {
    var days = java.util.List.of(new LinkStats.DayClick(0, 0L));
    assertThat(LinkStatsQueryService.halfLife(days)).isNull();
  }
}
