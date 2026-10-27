package com.example.short_link.link.stats.application.read;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.link.application.dto.LinkStats;
import java.util.List;
import org.junit.jupiter.api.Test;

class LinkStatsLifecycleReaderTest {

  @Test
  void halfLifeNullWhenEmpty() {
    assertThat(LinkStatsLifecycleReader.halfLife(List.of())).isNull();
  }

  @Test
  void halfLifeIsDayWhereCumulativeReachesHalf() {
    var days =
        List.of(
            new LinkStats.DayClick(0, 30L),
            new LinkStats.DayClick(1, 20L),
            new LinkStats.DayClick(2, 50L));
    assertThat(LinkStatsLifecycleReader.halfLife(days)).isEqualTo(1);
  }

  @Test
  void halfLifeFirstDayWhenItAlreadyReachesHalf() {
    var days = List.of(new LinkStats.DayClick(0, 90L), new LinkStats.DayClick(7, 10L));
    assertThat(LinkStatsLifecycleReader.halfLife(days)).isZero();
  }

  @Test
  void halfLifeNullWhenAllZero() {
    var days = List.of(new LinkStats.DayClick(0, 0L));
    assertThat(LinkStatsLifecycleReader.halfLife(days)).isNull();
  }
}
