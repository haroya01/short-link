package com.example.short_link.federation.application.trends;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class HostRestTest {

  private static final Instant NOW = Instant.parse("2026-10-27T00:00:00Z");

  @Test
  void eachRestDoublesFromAnHourAndWaitsAtMostADay() {
    HostRest rests = new HostRest(Clock.fixed(NOW, ZoneOffset.UTC));
    List<Duration> waits = new ArrayList<>();
    for (int i = 0; i < 8; i++) {
      waits.add(rests.rest("a.example"));
    }

    assertThat(waits)
        .containsExactly(
            Duration.ofHours(1),
            Duration.ofHours(2),
            Duration.ofHours(4),
            Duration.ofHours(8),
            Duration.ofHours(16),
            Duration.ofHours(32),
            Duration.ofHours(32),
            Duration.ofHours(32));
    assertThat(rests.resting("a.example", NOW.plus(Duration.ofHours(24)).minusSeconds(1))).isTrue();
    assertThat(rests.resting("a.example", NOW.plus(Duration.ofHours(24)))).isFalse();
  }

  @Test
  void aHostRestsUntilItsTimeIsUpAndAnAnswerStartsItOver() {
    HostRest rests = new HostRest(Clock.fixed(NOW, ZoneOffset.UTC));

    assertThat(rests.resting("a.example", NOW)).isFalse();
    rests.rest("a.example");
    rests.rest("a.example");
    assertThat(rests.resting("a.example", NOW.plus(Duration.ofHours(2)).minusSeconds(1))).isTrue();
    assertThat(rests.resting("a.example", NOW.plus(Duration.ofHours(2)))).isFalse();
    assertThat(rests.resting("b.example", NOW)).isFalse();

    rests.answered("a.example");
    assertThat(rests.resting("a.example", NOW)).isFalse();
    assertThat(rests.rest("a.example")).isEqualTo(Duration.ofHours(1));
  }
}
