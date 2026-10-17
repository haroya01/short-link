package com.example.short_link.federation.application.relay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class RelayBreakerTest {

  private static final Instant NOW = Instant.parse("2026-10-16T00:00:00Z");
  private static final RelayProperties PROPS =
      new RelayProperties(
          true, 30, 2_000, 100, Duration.ofSeconds(5), Duration.ofSeconds(4), null, List.of());

  private final Instant[] now = {NOW};
  private final Clock clock =
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
  private final RelayTally tally = new RelayTally(new SimpleMeterRegistry(), clock);
  private final RelaySubscriptions subscriptions = mock(RelaySubscriptions.class);
  private final RelayBreaker breaker = new RelayBreaker(PROPS, tally, subscriptions, clock);

  @Test
  void fiveSlowPostsInARowStopTheWorkAndAFastOneInBetweenDoesNot() {
    Duration slow = Duration.ofSeconds(5);
    for (int i = 0; i < 4; i++) {
      breaker.processed(slow, 0);
    }
    breaker.processed(Duration.ofMillis(200), 0);
    for (int i = 0; i < 4; i++) {
      breaker.processed(slow, 0);
    }
    assertThat(breaker.tripped()).isFalse();

    breaker.processed(slow, 0);
    breaker.processed(slow, 0);

    assertThat(breaker.tripped()).isTrue();
    assertThat(tally.today()).containsEntry("tripped-slow", 1L);
  }

  @Test
  void stoppingItselfAlsoTakesTheSettingOutOfStoreOnceSoARestartDoesNotStoreAgain() {
    for (int i = 0; i < 4; i++) {
      breaker.processed(Duration.ofSeconds(5), 0);
    }
    verify(subscriptions, never()).stopStoring();

    for (int i = 0; i < 3; i++) {
      breaker.processed(Duration.ofSeconds(5), 0);
    }

    verify(subscriptions).stopStoring();
  }

  @Test
  void aQueueFourFifthsFullForFiveMinutesStopsTheWorkUntilAnAdminStartsItAgain() {
    breaker.queued(80);
    now[0] = NOW.plus(RelayBreaker.BACKLOG_FOR).minusSeconds(1);
    breaker.queued(95);
    assertThat(breaker.tripped()).isFalse();

    breaker.queued(79);
    now[0] = NOW.plus(RelayBreaker.BACKLOG_FOR);
    breaker.queued(80);
    assertThat(breaker.tripped()).isFalse();

    now[0] = now[0].plus(RelayBreaker.BACKLOG_FOR);
    breaker.queued(80);
    assertThat(breaker.tripped()).isTrue();

    breaker.rearm();
    assertThat(breaker.tripped()).isFalse();
    breaker.queued(100);
    assertThat(breaker.tripped()).isFalse();
  }
}
