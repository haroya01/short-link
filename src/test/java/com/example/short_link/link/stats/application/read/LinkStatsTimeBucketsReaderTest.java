package com.example.short_link.link.stats.application.read;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LinkStatsTimeBucketsReaderTest {

  @Test
  void mapsMysqlDayOfWeekToEnumName() {
    assertThat(LinkStatsTimeBucketsReader.mapDayOfWeek(1)).isEqualTo("SUNDAY");
    assertThat(LinkStatsTimeBucketsReader.mapDayOfWeek(2)).isEqualTo("MONDAY");
    assertThat(LinkStatsTimeBucketsReader.mapDayOfWeek(3)).isEqualTo("TUESDAY");
    assertThat(LinkStatsTimeBucketsReader.mapDayOfWeek(4)).isEqualTo("WEDNESDAY");
    assertThat(LinkStatsTimeBucketsReader.mapDayOfWeek(5)).isEqualTo("THURSDAY");
    assertThat(LinkStatsTimeBucketsReader.mapDayOfWeek(6)).isEqualTo("FRIDAY");
    assertThat(LinkStatsTimeBucketsReader.mapDayOfWeek(7)).isEqualTo("SATURDAY");
  }

  @Test
  void mapsOutOfRangeDayOfWeekToUnknown() {
    assertThat(LinkStatsTimeBucketsReader.mapDayOfWeek(0)).isEqualTo("UNKNOWN");
    assertThat(LinkStatsTimeBucketsReader.mapDayOfWeek(8)).isEqualTo("UNKNOWN");
  }
}
