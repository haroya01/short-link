package com.example.short_link.common.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ReportZonesTest {

  @Test
  void takesAValidZoneAsIs() {
    assertThat(ReportZones.zoneOf("America/New_York")).isEqualTo(ZoneId.of("America/New_York"));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  ", "Not/A/Zone", "+25:00"})
  void fallsBackToSeoulForAMissingOrBrokenZone(String timezone) {
    assertThat(ReportZones.zoneOf(timezone)).isEqualTo(ZoneId.of("Asia/Seoul"));
  }

  @Test
  void theSqlOffsetFollowsDaylightSavingAtTheGivenInstant() {
    ZoneId newYork = ZoneId.of("America/New_York");

    assertThat(ReportZones.sqlOffset(newYork, Instant.parse("2026-07-01T00:00:00Z")))
        .isEqualTo("-04:00");
    assertThat(ReportZones.sqlOffset(newYork, Instant.parse("2026-01-01T00:00:00Z")))
        .isEqualTo("-05:00");
  }

  @Test
  void utcIsSpelledAsAnOffsetMysqlAccepts() {
    assertThat(ReportZones.sqlOffset(ZoneId.of("UTC"), Instant.EPOCH)).isEqualTo("+00:00");
  }

  @Test
  void aQuarterHourZoneKeepsItsMinutes() {
    assertThat(
            ReportZones.sqlOffset(
                ZoneId.of("Asia/Kathmandu"), Instant.parse("2026-07-01T00:00:00Z")))
        .isEqualTo("+05:45");
  }
}
