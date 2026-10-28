package com.example.short_link.federation.application.trends;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.seen.SeenViewer;
import com.example.short_link.federation.domain.SeenUri;
import com.example.short_link.federation.domain.repository.RemoteDwellRepository;
import com.example.short_link.federation.domain.repository.RemoteNoteReachRepository;
import com.example.short_link.federation.domain.repository.RemoteSeenRepository;
import com.example.short_link.federation.domain.repository.RemoteSeenRepository.Sighting;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RemoteSeenTest {

  private static final Instant NOW = Instant.parse("2026-10-22T03:00:00Z");
  private static final SeenViewer MEMBER = SeenViewer.member(7L);
  private static final SeenViewer DEVICE =
      SeenViewer.of(null, "6f1c2a3b-4d5e-4f60-8a71-92b3c4d5e6f7");

  private final RemoteSeenRepository repository = mock(RemoteSeenRepository.class);
  private final RemoteDwellRepository dwells = mock(RemoteDwellRepository.class);
  private final RemoteNoteReachRepository notes = mock(RemoteNoteReachRepository.class);
  private final Clock clock = mock(Clock.class);
  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();

  @BeforeEach
  void now() {
    when(clock.instant()).thenReturn(NOW);
    when(repository.seenBefore(any(), any(), any())).thenReturn(Set.of());
  }

  private RemoteSeen seen(int maxBuffered) {
    return new RemoteSeen(
        repository, dwells, notes, new SeenProperties(maxBuffered, null), clock, meters);
  }

  private double counted(String result) {
    return meters.counter("federation_seen", "result", result).count();
  }

  private static String post(int n) {
    return "https://mastodon.example/users/a/statuses/" + n;
  }

  @SuppressWarnings("unchecked")
  private List<List<Sighting>> inserts(int times) {
    ArgumentCaptor<List<Sighting>> rows = ArgumentCaptor.forClass(List.class);
    verify(repository, times(times)).insert(rows.capture());
    return rows.getAllValues().stream().map(List::copyOf).toList();
  }

  @Test
  void aFlushWritesEverythingHeldAsOneInsertWithTheFirstSighting() {
    RemoteSeen seen = seen(0);
    seen.record(MEMBER, List.of(post(1), post(2)));
    when(clock.instant()).thenReturn(NOW.plusSeconds(3));
    seen.record(MEMBER, List.of(post(1)));
    seen.record(DEVICE, List.of(post(1)));

    assertThat(seen.flush()).isEqualTo(3);

    List<Sighting> rows = inserts(1).getFirst();
    assertThat(rows)
        .containsExactlyInAnyOrder(
            new Sighting(MEMBER, SeenUri.of(post(1)), NOW),
            new Sighting(MEMBER, SeenUri.of(post(2)), NOW),
            new Sighting(DEVICE, SeenUri.of(post(1)), NOW.plusSeconds(3)));
    assertThat(seen.flush()).isZero();
    assertThat(counted("received")).isEqualTo(4);
    assertThat(counted("written")).isEqualTo(3);
    assertThat(meters.timer("federation_seen_flush").count()).isEqualTo(1);
  }

  @Test
  void aBigFlushGoesInRowsPerInsertAtATime() {
    RemoteSeen seen = seen(0);
    for (int request = 0; request < 25; request++) {
      int from = request * RemoteSeen.MAX_PER_REQUEST;
      seen.record(
          MEMBER,
          IntStream.range(from, from + RemoteSeen.MAX_PER_REQUEST).mapToObj(n -> post(n)).toList());
    }

    assertThat(seen.flush()).isEqualTo(2_500);

    assertThat(inserts(3).stream().map(List::size))
        .containsExactly(RemoteSeen.ROWS_PER_INSERT, RemoteSeen.ROWS_PER_INSERT, 500);
  }

  @Test
  void pastTheBufferCapNewSightingsAreDroppedAndCounted() {
    RemoteSeen seen = seen(3);
    seen.record(MEMBER, List.of(post(1), post(2)));
    seen.record(DEVICE, List.of(post(1), post(2), post(3)));
    seen.record(MEMBER, List.of(post(1)));

    assertThat(counted("dropped")).isEqualTo(2);
    assertThat(seen.flush()).isEqualTo(3);
    seen.record(DEVICE, List.of(post(2), post(3)));
    assertThat(seen.flush()).isEqualTo(2);
  }

  @Test
  void onlyTheFirstHundredWellFormedWebAddressesAreTaken() {
    RemoteSeen seen = seen(0);
    List<String> sent = new ArrayList<>();
    sent.add("ftp://mastodon.example/a");
    sent.add("not a uri");
    sent.add("https:///no-host");
    sent.add(null);
    sent.add("https://mastodon.example/" + "a".repeat(SeenUri.MAX_LENGTH));
    sent.add("HTTP://mastodon.example/users/a/statuses/x");
    IntStream.range(0, 120).forEach(n -> sent.add(post(n)));

    seen.record(MEMBER, sent);

    assertThat(counted("received")).isEqualTo(95);
    seen.record(MEMBER, List.of("javascript:alert(1)"));
    seen.record(MEMBER, null);
    assertThat(counted("received")).isEqualTo(95);
  }

  @Test
  void whatIsHeldCountsAsSeenOnlyWhenItWasSeenBeforeAsOf() {
    RemoteSeen seen = seen(0);
    seen.record(DEVICE, List.of(post(1)));
    when(clock.instant()).thenReturn(NOW.plusMillis(1));
    seen.record(DEVICE, List.of(post(2)));
    when(repository.seenBefore(eq(DEVICE), any(), eq(NOW.plusMillis(1))))
        .thenReturn(Set.of(SeenUri.of(post(3))));

    assertThat(
            seen.seenBefore(DEVICE, List.of(post(1), post(2), post(3), post(4)), NOW.plusMillis(1)))
        .containsExactlyInAnyOrder(post(1), post(3));
    assertThat(seen.seenBefore(DEVICE, List.of(post(1)), NOW)).isEmpty();
    assertThat(seen.seenBefore(MEMBER, List.of(post(1)), NOW.plusSeconds(1))).isEmpty();
  }

  @Test
  void aFailedInsertDropsItsRowsAndTheRestStillGo() {
    RemoteSeen seen = seen(0);
    IntStream.range(0, 12)
        .forEach(
            request ->
                seen.record(
                    MEMBER,
                    IntStream.range(request * 100, request * 100 + 100)
                        .mapToObj(n -> post(n))
                        .toList()));
    doThrow(new IllegalStateException("down")).doNothing().when(repository).insert(any());

    assertThat(seen.flush()).isEqualTo(200);

    assertThat(counted("dropped")).isEqualTo(RemoteSeen.ROWS_PER_INSERT);
    assertThat(counted("written")).isEqualTo(200);
    assertThat(seen.flush()).isZero();
  }

  @Test
  void theCleanupDeletesInBatchesUntilABatchComesBackShort() {
    when(repository.deleteBefore(any(), anyInt())).thenReturn(5_000, 5_000, 12);

    assertThat(seen(0).sweep()).isEqualTo(10_012);

    verify(repository, times(3))
        .deleteBefore(NOW.minus(Duration.ofDays(14)), RemoteSeen.DELETE_BATCH);
  }

  @Test
  void nothingHeldAsksNothing() {
    RemoteSeen seen = seen(0);

    assertThat(seen.flush()).isZero();
    assertThat(seen.seenBefore(DEVICE, List.of(), NOW)).isEmpty();

    verify(repository, never()).insert(any());
    verify(repository, never()).seenBefore(any(), any(), any());
  }

  @SuppressWarnings("unchecked")
  private List<RemoteDwellRepository.Dwell> dwelt() {
    ArgumentCaptor<List<RemoteDwellRepository.Dwell>> rows = ArgumentCaptor.forClass(List.class);
    verify(dwells).record(rows.capture());
    return rows.getValue();
  }

  @Test
  void aDwellIsHeldAtItsLongestCutToTenMinutesAndWrittenByNoteAtTheFlush() {
    when(notes.idsByUri(any())).thenReturn(Map.of(post(1), 11L, post(2), 12L));
    RemoteSeen seen = seen(0);

    seen.record(
        MEMBER,
        List.of(post(1)),
        List.of(
            new RemoteSeen.Dwell(post(1), 3_000),
            new RemoteSeen.Dwell(post(1), 9_000),
            new RemoteSeen.Dwell(post(2), 900_000),
            new RemoteSeen.Dwell(post(3), 999),
            new RemoteSeen.Dwell("ftp://mastodon.example/a", 5_000)),
        "for-you");
    seen.record(MEMBER, List.of(), List.of(new RemoteSeen.Dwell(post(1), 4_000)), "for-you");
    seen.record(DEVICE, null, List.of(new RemoteSeen.Dwell(post(4), 2_000)), null);

    assertThat(seen.flush()).isEqualTo(1);

    assertThat(dwelt())
        .containsExactlyInAnyOrder(
            new RemoteDwellRepository.Dwell(MEMBER, 11L, 9_000, NOW),
            new RemoteDwellRepository.Dwell(MEMBER, 12L, RemoteSeen.MAX_DWELL_MS, NOW));
    assertThat(meters.counter("federation_dwell_rows", "result", "written").count()).isEqualTo(2);
    assertThat(meters.counter("federation_dwell_rows", "result", "unknown").count()).isEqualTo(1);
    assertThat(meters.counter("federation_seen_feed", "feed", "for-you").count()).isEqualTo(1);
    assertThat(meters.summary("federation_dwell", "feed", "for-you").count()).isEqualTo(3);
    assertThat(meters.summary("federation_dwell", "feed", "unknown").count()).isEqualTo(1);
    seen.flush();
    verify(notes, times(1)).idsByUri(any());
  }

  @Test
  void aFeedOutsideTheKnownOnesIsNotCountedAndNoDwellAsksNothing() {
    RemoteSeen seen = seen(0);

    seen.record(MEMBER, List.of(post(1)), null, "home");
    seen.record(MEMBER, List.of(post(2)), List.of(new RemoteSeen.Dwell(post(2), 10)), "latest");

    assertThat(meters.find("federation_seen_feed").tag("feed", "home").counter()).isNull();
    assertThat(meters.counter("federation_seen_feed", "feed", "latest").count()).isEqualTo(1);
    assertThat(seen.flush()).isEqualTo(2);
    verify(notes, never()).idsByUri(any());
  }

  @Test
  void pastTheBufferCapNewDwellsAreDroppedButAHeldOneStillGrows() {
    when(notes.idsByUri(any())).thenReturn(Map.of(post(1), 11L));
    RemoteSeen seen = seen(1);

    seen.record(
        MEMBER,
        null,
        List.of(new RemoteSeen.Dwell(post(1), 2_000), new RemoteSeen.Dwell(post(2), 2_000)),
        "latest");
    seen.record(MEMBER, null, List.of(new RemoteSeen.Dwell(post(1), 5_000)), "latest");

    assertThat(meters.counter("federation_dwell_rows", "result", "dropped").count()).isEqualTo(1);
    seen.flush();
    assertThat(dwelt()).containsExactly(new RemoteDwellRepository.Dwell(MEMBER, 11L, 5_000, NOW));
  }

  @Test
  void aFailedDwellWriteDropsItsRowsAndTheSightingsStillGo() {
    when(notes.idsByUri(any())).thenReturn(Map.of(post(1), 11L));
    doThrow(new IllegalStateException("down")).when(dwells).record(any());
    RemoteSeen seen = seen(0);

    seen.record(MEMBER, List.of(post(1)), List.of(new RemoteSeen.Dwell(post(1), 2_000)), null);

    assertThat(seen.flush()).isEqualTo(1);
    assertThat(meters.counter("federation_dwell_rows", "result", "dropped").count()).isEqualTo(1);
  }

  @Test
  void theCleanupAlsoDeletesDwellsOlderThanThirtyDaysInBatches() {
    when(dwells.deleteBefore(any(), anyInt())).thenReturn(5_000, 3);

    assertThat(seen(0).sweep()).isEqualTo(5_003);

    verify(dwells, times(2)).deleteBefore(NOW.minus(Duration.ofDays(30)), RemoteSeen.DELETE_BATCH);
  }
}
