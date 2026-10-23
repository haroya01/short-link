package com.example.short_link.federation.application.trends;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.seen.Unseen;
import com.example.short_link.federation.domain.SeenItem;
import com.example.short_link.federation.domain.SeenViewer;
import com.example.short_link.federation.domain.repository.LocalSeenRepository;
import com.example.short_link.federation.domain.repository.LocalSeenRepository.Sighting;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LocalSeenTest {

  private static final Instant NOW = Instant.parse("2026-10-23T03:00:00Z");
  private static final SeenViewer MEMBER = SeenViewer.member(7L);
  private static final SeenViewer DEVICE =
      SeenViewer.of(null, "6f1c2a3b-4d5e-4f60-8a71-92b3c4d5e6f7");
  private static final SeenItem NOTE_1 = new SeenItem(SeenItem.NOTE, 1);
  private static final SeenItem NOTE_2 = new SeenItem(SeenItem.NOTE, 2);
  private static final SeenItem POST_1 = new SeenItem(SeenItem.POST, 1);

  private final LocalSeenRepository repository = mock(LocalSeenRepository.class);
  private final Clock clock = mock(Clock.class);
  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();

  @BeforeEach
  void now() {
    when(clock.instant()).thenReturn(NOW);
  }

  private LocalSeen seen(int maxBuffered) {
    return new LocalSeen(repository, new SeenProperties(maxBuffered, null), clock, meters);
  }

  private double counted(String result) {
    return meters.counter("home_seen", "result", result).count();
  }

  @SuppressWarnings("unchecked")
  private List<List<Sighting>> inserts(int times) {
    ArgumentCaptor<List<Sighting>> rows = ArgumentCaptor.forClass(List.class);
    verify(repository, times(times)).insert(rows.capture());
    return rows.getAllValues().stream().map(List::copyOf).toList();
  }

  @Test
  void onlyKurlKeysAreKeptAndAFlushWritesThemAsOneInsertWithTheFirstSighting() {
    LocalSeen seen = seen(0);
    seen.record(
        MEMBER,
        List.of("kurl:note:1", "https://a.example/users/a/statuses/1", "kurl:post:1", "kurl:x:1"));
    when(clock.instant()).thenReturn(NOW.plusSeconds(3));
    seen.record(MEMBER, List.of("kurl:note:1", "kurl:note:2"));
    seen.record(DEVICE, List.of("kurl:note:1"));

    assertThat(seen.flush()).isEqualTo(4);

    assertThat(inserts(1).getFirst())
        .containsExactlyInAnyOrder(
            new Sighting(MEMBER, NOTE_1, NOW),
            new Sighting(MEMBER, POST_1, NOW),
            new Sighting(MEMBER, NOTE_2, NOW.plusSeconds(3)),
            new Sighting(DEVICE, NOTE_1, NOW.plusSeconds(3)));
    assertThat(seen.flush()).isZero();
    assertThat(counted("received")).isEqualTo(5);
    assertThat(counted("written")).isEqualTo(4);
    assertThat(meters.timer("home_seen_flush").count()).isEqualTo(1);
  }

  @Test
  void aRequestWithoutKurlKeysLeavesNothingToWrite() {
    LocalSeen seen = seen(0);

    assertThat(seen.record(MEMBER, null)).isZero();
    assertThat(seen.record(MEMBER, List.of("https://a.example/users/a/statuses/1"))).isZero();
    assertThat(seen.flush()).isZero();
    verify(repository, never()).insert(any());
  }

  @Test
  void onlyTheFirstHundredOfARequestAreRead() {
    LocalSeen seen = seen(0);
    List<String> sent = new ArrayList<>();
    sent.add("https://a.example/users/a/statuses/1");
    IntStream.rangeClosed(1, RemoteSeen.MAX_PER_REQUEST).forEach(n -> sent.add("kurl:note:" + n));

    assertThat(seen.record(MEMBER, sent)).isEqualTo(RemoteSeen.MAX_PER_REQUEST - 1);
  }

  @Test
  void aBigFlushGoesInRowsPerInsertAtATime() {
    LocalSeen seen = seen(0);
    for (int request = 0; request < 25; request++) {
      int from = request * RemoteSeen.MAX_PER_REQUEST + 1;
      seen.record(
          MEMBER,
          IntStream.range(from, from + RemoteSeen.MAX_PER_REQUEST)
              .mapToObj(n -> "kurl:post:" + n)
              .toList());
    }

    assertThat(seen.flush()).isEqualTo(2_500);
    assertThat(inserts(3).stream().map(List::size))
        .containsExactly(LocalSeen.ROWS_PER_INSERT, LocalSeen.ROWS_PER_INSERT, 500);
  }

  @Test
  void pastTheBufferCapNewSightingsAreDroppedAndCounted() {
    LocalSeen seen = seen(2);
    seen.record(MEMBER, List.of("kurl:note:1"));
    seen.record(DEVICE, List.of("kurl:note:1", "kurl:note:2"));

    assertThat(counted("dropped")).isEqualTo(1);
    assertThat(seen.flush()).isEqualTo(2);
  }

  @Test
  void aChunkThatFailsToWriteIsDroppedAndCountedWhileTheRestGoIn() {
    LocalSeen seen = seen(0);
    for (int request = 0; request < 15; request++) {
      int from = request * RemoteSeen.MAX_PER_REQUEST + 1;
      seen.record(
          MEMBER,
          IntStream.range(from, from + RemoteSeen.MAX_PER_REQUEST)
              .mapToObj(n -> "kurl:note:" + n)
              .toList());
    }
    doThrow(new IllegalStateException("down")).doNothing().when(repository).insert(any());

    assertThat(seen.flush()).isEqualTo(500);
    assertThat(counted("dropped")).isEqualTo(1_000);
    assertThat(counted("written")).isEqualTo(500);
  }

  @Test
  void whatIsHeldBeforeAsOfIsLeftOutByIdEvenWhileItIsBeingWritten() {
    LocalSeen seen = seen(0);
    seen.record(MEMBER, List.of("kurl:note:1", "kurl:post:1"));
    when(clock.instant()).thenReturn(NOW.plusSeconds(5));
    seen.record(MEMBER, List.of("kurl:note:2"));
    seen.record(DEVICE, List.of("kurl:note:9"));
    AtomicReference<Unseen> during = new AtomicReference<>();
    doAnswer(
            call -> {
              during.set(seen.unseen(MEMBER, NOW.plusSeconds(1)));
              return null;
            })
        .when(repository)
        .insert(any());

    Unseen before = seen.unseen(MEMBER, NOW.plusSeconds(1));
    seen.flush();
    Unseen after = seen.unseen(MEMBER, NOW.plusSeconds(1));

    assertThat(before.heldNotes()).containsExactly(1L);
    assertThat(before.heldPosts()).containsExactly(1L);
    assertThat(before.viewerKind()).isEqualTo(SeenViewer.MEMBER);
    assertThat(before.viewerKey()).isEqualTo(MEMBER.key());
    assertThat(before.asOf()).isEqualTo(NOW.plusSeconds(1));
    assertThat(during.get().heldNotes()).containsExactly(1L);
    assertThat(after.heldNotes()).containsExactly(-1L);
    assertThat(after.heldPosts()).containsExactly(-1L);
  }

  @Test
  void theHeldListStopsAtItsCap() {
    LocalSeen seen = seen(0);
    for (int request = 0; request < 11; request++) {
      int from = request * RemoteSeen.MAX_PER_REQUEST + 1;
      seen.record(
          MEMBER,
          IntStream.range(from, from + RemoteSeen.MAX_PER_REQUEST)
              .mapToObj(n -> "kurl:note:" + n)
              .toList());
    }

    assertThat(seen.unseen(MEMBER, NOW.plusSeconds(1)).heldNotes()).hasSize(LocalSeen.MAX_HELD);
  }

  @Test
  void seenBeforeJoinsWhatIsStoredWithWhatIsHeldBeforeAsOf() {
    LocalSeen seen = seen(0);
    seen.record(MEMBER, List.of("kurl:note:2"));
    when(clock.instant()).thenReturn(NOW.plusSeconds(10));
    seen.record(MEMBER, List.of("kurl:post:1"));
    when(repository.seenBefore(eq(MEMBER), any(), eq(NOW.plusSeconds(5))))
        .thenReturn(Set.of(NOTE_1));

    assertThat(seen.seenBefore(MEMBER, List.of(NOTE_1, NOTE_2, POST_1), NOW.plusSeconds(5)))
        .containsExactlyInAnyOrder(NOTE_1, NOTE_2);
    assertThat(seen.seenBefore(MEMBER, List.of(), NOW)).isEmpty();
    verify(repository, times(1)).seenBefore(any(), any(), any());
  }

  @Test
  void aSweepDeletesPastTheKeepInBatchesUntilAShortOne() {
    when(repository.deleteBefore(NOW.minus(Duration.ofDays(14)), LocalSeen.DELETE_BATCH))
        .thenReturn(LocalSeen.DELETE_BATCH, 12);

    assertThat(seen(0).sweep()).isEqualTo(LocalSeen.DELETE_BATCH + 12);
    verify(repository, times(2)).deleteBefore(any(), anyInt());
  }

  @Test
  void whatIsHeldIsWrittenOnShutdown() {
    LocalSeen seen = seen(0);
    seen.record(MEMBER, List.of("kurl:note:1"));

    seen.flushOnShutdown();

    assertThat(inserts(1).getFirst()).containsExactly(new Sighting(MEMBER, NOTE_1, NOW));
  }
}
