package com.example.short_link.link.notice.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class ClickSpikeTest {

  private final ClickSpike spike = new ClickSpike(10, 3.0);

  @Test
  void aLinkIsMeasuredFromItsMinimumClicks() {
    assertThat(spike.considers(9)).isFalse();
    assertThat(spike.considers(10)).isTrue();
  }

  @Test
  void theLastHourNeedsThreeClicks() {
    assertThat(spike.busy(2)).isFalse();
    assertThat(spike.busy(3)).isTrue();
  }

  @Test
  void exactlyTheRatioCountsAsASpike() {
    assertThat(spike.timesUsual(3, 24)).isEqualTo(OptionalLong.of(3));
    assertThat(spike.timesUsual(3, 25)).isEmpty();
  }

  @Test
  void aQuietDayAveragesAtLeastHalfAClick() {
    assertThat(spike.timesUsual(3, 0)).isEqualTo(OptionalLong.of(6));
    assertThat(spike.timesUsual(3, 12)).isEqualTo(OptionalLong.of(6));
    assertThat(spike.timesUsual(3, 13)).isEqualTo(OptionalLong.of(6));
    assertThat(spike.timesUsual(3, 18)).isEqualTo(OptionalLong.of(4));
  }

  @Test
  void theRatioIsRoundedToTheNearestWhole() {
    assertThat(spike.timesUsual(7, 48)).isEqualTo(OptionalLong.of(4));
    assertThat(spike.timesUsual(5, 24)).isEqualTo(OptionalLong.of(5));
    assertThat(spike.timesUsual(10, 72)).isEqualTo(OptionalLong.of(3));
  }
}
