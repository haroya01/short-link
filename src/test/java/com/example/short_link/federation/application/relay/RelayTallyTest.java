package com.example.short_link.federation.application.relay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RelayTallyTest {

  @Test
  void todayIsTheSeoulDayAndTheMeterKeepsCountingAcrossIt() {
    Instant[] now = {Instant.parse("2026-10-16T14:59:00Z")};
    Clock clock =
        new Clock() {
          @Override
          public ZoneId getZone() {
            return ZoneOffset.UTC;
          }

          @Override
          public Clock withZone(ZoneId zone) {
            return Clock.fixed(now[0], zone);
          }

          @Override
          public Instant instant() {
            return now[0];
          }
        };
    SimpleMeterRegistry meters = new SimpleMeterRegistry();
    RelayTally tally = new RelayTally(meters, clock);

    tally.count("queued");
    tally.count("queued");
    tally.count("language");
    assertThat(tally.today()).isEqualTo(Map.of("language", 1L, "queued", 2L));

    now[0] = Instant.parse("2026-10-16T15:00:00Z");
    assertThat(tally.today("queued")).isZero();
    tally.count("queued");

    assertThat(tally.today()).isEqualTo(Map.of("queued", 1L));
    assertThat(meters.counter("federation.relay", "outcome", "queued").count()).isEqualTo(3);
  }

  @Test
  void buildingTheTallyDoesNotReadTheClock() {
    Clock unreadable =
        new Clock() {
          @Override
          public ZoneId getZone() {
            throw new AssertionError("read at construction");
          }

          @Override
          public Clock withZone(ZoneId zone) {
            throw new AssertionError("read at construction");
          }

          @Override
          public Instant instant() {
            throw new AssertionError("read at construction");
          }
        };

    assertThatCode(() -> new RelayTally(new SimpleMeterRegistry(), unreadable))
        .doesNotThrowAnyException();
  }
}
