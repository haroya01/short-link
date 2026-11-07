package com.example.short_link.common.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OffsetPeriodTest {
  private static final Instant FROM = Instant.parse("2026-10-11T15:00:00Z");
  private static final Instant UNTIL = Instant.parse("2026-11-10T15:00:01Z");

  @Test
  void aZoneWithoutTransitionsIsOnePeriod() {
    assertThat(OffsetPeriod.between(ZoneId.of("Asia/Seoul"), FROM, UNTIL))
        .containsExactly(new OffsetPeriod(FROM, UNTIL, "+09:00"));
  }

  @Test
  void aDaylightSavingEndSplitsThePeriodAtTheTransition() {
    Instant fallBack = Instant.parse("2026-11-01T06:00:00Z");

    assertThat(OffsetPeriod.between(ZoneId.of("America/New_York"), FROM, UNTIL))
        .containsExactly(
            new OffsetPeriod(FROM, fallBack, "-04:00"),
            new OffsetPeriod(fallBack, UNTIL, "-05:00"));
  }

  @Test
  void aTransitionExactlyAtTheEndDoesNotAddAnEmptyPeriod() {
    Instant fallBack = Instant.parse("2026-11-01T06:00:00Z");

    assertThat(OffsetPeriod.between(ZoneId.of("America/New_York"), FROM, fallBack))
        .containsExactly(new OffsetPeriod(FROM, fallBack, "-04:00"));
  }

  @Test
  void theSameBucketReadInBothOffsetsAddsUp() {
    List<OffsetPeriod> periods = OffsetPeriod.between(ZoneId.of("America/New_York"), FROM, UNTIL);
    Map<String, List<Map.Entry<Integer, Long>>> rows =
        Map.of(
            "-04:00", List.of(Map.entry(1, 3L), Map.entry(2, 1L)),
            "-05:00", List.of(Map.entry(1, 2L)));

    assertThat(
            OffsetPeriod.sum(
                periods, p -> rows.get(p.offset()), Map.Entry::getKey, Map.Entry::getValue))
        .isEqualTo(Map.of(1, 5L, 2, 1L));
  }
}
