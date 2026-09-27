package com.example.short_link.post.application.write;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ScheduledPublicationBackoffTest {
  private static final Instant START = Instant.parse("2026-09-18T00:00:00Z");

  private final ScheduledPublicationBackoff backoff = new ScheduledPublicationBackoff();

  @Test
  void delaysDoubleFromOneMinuteAndStopGrowingAtOneHour() {
    List<Long> delays = new ArrayList<>();
    Instant now = START;
    for (int i = 0; i < 9; i++) {
      Instant retryAt = backoff.recordFailure(7L, now).retryAt();
      delays.add(Duration.between(now, retryAt).toMinutes());
      now = retryAt;
    }

    assertThat(delays).containsExactly(1L, 2L, 4L, 8L, 16L, 32L, 60L, 60L, 60L);
  }

  @Test
  void aPostIsDueAgainExactlyAtItsRetryTime() {
    Instant retryAt = backoff.recordFailure(7L, START).retryAt();

    assertThat(backoff.isDue(7L, retryAt.minusSeconds(1))).isFalse();
    assertThat(backoff.isDue(7L, retryAt)).isTrue();
    assertThat(backoff.isDue(8L, START)).isTrue();
  }

  @Test
  void successAndLeavingTheScheduleBothClearTheFailure() {
    backoff.recordFailure(7L, START);
    backoff.recordFailure(8L, START);

    backoff.recordSuccess(7L);
    backoff.forgetAllExcept(Set.of(9L));

    assertThat(backoff.isDue(7L, START)).isTrue();
    assertThat(backoff.isDue(8L, START)).isTrue();
    assertThat(backoff.recordFailure(8L, START).attempts()).isEqualTo(1);
  }
}
