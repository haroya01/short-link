package com.example.short_link.federation.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.federation.application.relay.RelayTally;
import com.example.short_link.federation.domain.repository.FederationRelayedNoteRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class RelayRetentionJobTest {

  private static final Instant NOW = Instant.parse("2026-10-16T19:20:00Z");

  private final FederationRelayedNoteRepository relayed =
      mock(FederationRelayedNoteRepository.class);
  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
  private final RelayTally tally = new RelayTally(meters);

  @Test
  void notesSevenDaysOldGoInBatchesOfTwoHundredWithAPauseBetweenUntilABatchComesUpShort()
      throws Exception {
    Instant before = Instant.parse("2026-10-09T19:20:00Z");
    when(relayed.deleteExpired(before, 200)).thenReturn(200, 200, 13);
    RelayRetentionJob job =
        new RelayRetentionJob(relayed, tally, clock, Duration.ofMillis(60), false);

    long started = System.nanoTime();
    assertThat(job.sweep()).isEqualTo(413);

    assertThat(Duration.ofNanos(System.nanoTime() - started))
        .isGreaterThanOrEqualTo(Duration.ofMillis(120));
    verify(relayed, times(3)).deleteExpired(before, 200);
    assertThat(tally.today("expired")).isEqualTo(413);
    assertThat(meters.get("federation.relay").tag("outcome", "expired").counter().count())
        .isEqualTo(413);
  }

  @Test
  void aRunWithNothingToDeleteAsksOnce() throws Exception {
    when(relayed.deleteExpired(any(), anyInt())).thenReturn(0);

    assertThat(new RelayRetentionJob(relayed, tally, clock, Duration.ZERO, false).sweep()).isZero();
    verify(relayed).deleteExpired(any(), anyInt());
  }

  @Test
  void noThreadRunsWhereSchedulingIsOff() {
    new RelayRetentionJob(relayed, tally, clock, Duration.ZERO, false).scheduled();

    verifyNoInteractions(relayed);
    assertThat(Thread.getAllStackTraces().keySet())
        .noneMatch(thread -> thread.getName().equals("kurl-relay-retention"));
  }
}
