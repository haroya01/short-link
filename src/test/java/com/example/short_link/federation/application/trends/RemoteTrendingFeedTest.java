package com.example.short_link.federation.application.trends;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.common.note.NoteViewReader;
import com.example.short_link.federation.domain.SeenViewer;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RemoteTrendingFeedTest {

  private final TrendingPosts trending = mock(TrendingPosts.class);
  private final TrendingAuthors authors = mock(TrendingAuthors.class);
  private final NoteViewReader notes = mock(NoteViewReader.class);
  private final LatestPosts latest = mock(LatestPosts.class);
  private final RemoteSeen seen = mock(RemoteSeen.class);
  private final Clock clock = mock(Clock.class);
  private final RemoteTrendingFeed feed = feed(false);

  private RemoteTrendingFeed feed(boolean pulling) {
    return new RemoteTrendingFeed(
        trending,
        latest,
        new LatestProperties(true, List.of(), 0, 0, null, null, pulling, null, 0, 0),
        authors,
        notes,
        seen,
        clock);
  }

  private static List<NoteViewReader.Listed> listed(long... ids) {
    return Arrays.stream(ids)
        .mapToObj(id -> new NoteViewReader.Listed(id, "n" + id, "https://x/s/" + id))
        .toList();
  }

  private static final Instant AS_OF = Instant.parse("2026-10-22T03:00:00Z");
  private static final SeenViewer DEVICE =
      SeenViewer.of(null, "6f1c2a3b-4d5e-4f60-8a71-92b3c4d5e6f7");

  @BeforeEach
  void now() {
    when(clock.instant()).thenReturn(AS_OF);
  }

  private void seenBefore(String... uris) {
    when(seen.seenBefore(eq(DEVICE), any(), any()))
        .thenAnswer(
            call -> {
              Collection<String> asked = call.getArgument(1);
              return Set.copyOf(asked.stream().filter(List.of(uris)::contains).toList());
            });
  }

  private void below(int offset, int size, boolean more, String... uris) {
    List<TrendingNote> page = Arrays.stream(uris).map(RemoteTrendingFeedTest::item).toList();
    when(latest.page(null, offset, size)).thenReturn(new LatestPosts.Page(page, more, List.of()));
  }

  private static List<String> uris(RemoteTrendingFeed.Page page) {
    return page.items().stream().map(item -> ((TrendingNote) item).uri()).toList();
  }

  private static RemoteTrendingFeed.KeptNote kept(long id) {
    return new RemoteTrendingFeed.KeptNote("n" + id, "https://x/s/" + id);
  }

  private static TrendingNote item() {
    return mock(TrendingNote.class);
  }

  @Test
  void aMemberPagesThroughTheTrendingPostsAndThenOnThroughTheNotesKeptHere() {
    TrendingNote a = item();
    TrendingNote b = item();
    when(trending.page(7L, 0, 2))
        .thenReturn(new TrendingPosts.Page(List.of(a, b), true, List.of("https://x/u/1")));
    when(trending.page(7L, 2, 2)).thenReturn(new TrendingPosts.Page(List.of(a), false, List.of()));
    when(notes.received(7L, null, 3))
        .thenReturn(
            List.of(
                new NoteViewReader.Listed(30L, "n30", null),
                new NoteViewReader.Listed(29L, "n29", null),
                new NoteViewReader.Listed(28L, "n28", null)));
    when(notes.received(7L, 29L, 3))
        .thenReturn(List.of(new NoteViewReader.Listed(28L, "n28", null)));

    RemoteTrendingFeed.Page first = feed.page(7L, null, 2);
    RemoteTrendingFeed.Page second = feed.page(7L, first.next(), 2);
    RemoteTrendingFeed.Page third = feed.page(7L, second.next(), 2);
    RemoteTrendingFeed.Page last = feed.page(7L, third.next(), 2);

    assertThat(first.items()).containsExactly(a, b);
    assertThat(first.next()).isEqualTo("t2");
    assertThat(second.next()).isEqualTo("r");
    assertThat(third.items()).containsExactly("n30", "n29");
    assertThat(third.next()).isEqualTo("r29");
    assertThat(last.items()).containsExactly("n28");
    assertThat(last.next()).isNull();
    verify(authors).warm(List.of("https://x/u/1"));
  }

  @Test
  void someoneSignedOutSeesOnlyTheTrendingPostsAndWarmsNoAuthors() {
    when(trending.page(null, 0, 20))
        .thenReturn(new TrendingPosts.Page(List.of(item()), false, List.of("https://x/u/1")));

    RemoteTrendingFeed.Page page = feed.page(null, "garbage", 20);

    assertThat(page.next()).isNull();
    assertThat(feed.page(null, "r", 20).items()).isEmpty();
    verify(authors, never()).warm(any());
    verifyNoInteractions(notes);
  }

  private static TrendingNote item(String uri) {
    TrendingNote note = mock(TrendingNote.class);
    when(note.uri()).thenReturn(uri);
    return note;
  }

  @Test
  void aRefreshSinceTheTopItemGetsOnlyWhatCameAboveIt() {
    TrendingNote newest = item("https://x/s/3");
    TrendingNote seen = item("https://x/s/2");
    TrendingNote older = item("https://x/s/1");
    when(latest.page(7L, 0, TrendingPosts.MAX_LIMIT))
        .thenReturn(new LatestPosts.Page(List.of(newest, seen, older), false, List.of()));

    RemoteTrendingFeed.Page fresh = feed.latest(7L, null, null, "https://x/s/2", 20);
    RemoteTrendingFeed.Page none = feed.latest(7L, null, null, "https://x/s/3", 20);

    assertThat(fresh.items()).containsExactly(newest);
    assertThat(fresh.next()).isNull();
    assertThat(fresh.gap()).isFalse();
    assertThat(none.items()).isEmpty();
    assertThat(none.gap()).isFalse();
  }

  @Test
  void aMarkerOutOfReachStartsTheListOverWithAGap() {
    TrendingNote newest = item("https://x/s/9");
    when(latest.page(7L, 0, TrendingPosts.MAX_LIMIT))
        .thenReturn(new LatestPosts.Page(List.of(newest), true, List.of()));
    when(latest.page(7L, 0, 20)).thenReturn(new LatestPosts.Page(List.of(newest), true, List.of()));

    RemoteTrendingFeed.Page page = feed.latest(7L, null, null, "https://x/s/1", 20);

    assertThat(page.items()).containsExactly(newest);
    assertThat(page.next()).isEqualTo("t1");
    assertThat(page.gap()).isTrue();
    assertThat(feed.latest(7L, null, null, null, 20).gap()).isNull();
  }

  @Test
  void oncePullingIsOnAnyonePagesTheKeptLatestByTheLastNoteShownAndMemoryIsNotRead() {
    RemoteTrendingFeed stored = feed(true);
    when(notes.latest(null, null, 3)).thenReturn(listed(30, 29, 28));
    when(notes.latest(null, 29L, 3)).thenReturn(listed(28));

    RemoteTrendingFeed.Page first = stored.latest(null, null, null, null, 2);
    RemoteTrendingFeed.Page last = stored.latest(null, null, first.next(), null, 2);

    assertThat(first.items()).containsExactly(kept(30), kept(29));
    assertThat(first.next()).isEqualTo("r29");
    assertThat(first.gap()).isNull();
    assertThat(last.items()).containsExactly(kept(28));
    assertThat(last.next()).isNull();
    verifyNoInteractions(latest, authors, seen);
  }

  @Test
  void oncePullingIsOnARefreshSinceTheNewestNoteGetsWhatCameAboveItOrStartsOverWithAGap() {
    RemoteTrendingFeed stored = feed(true);
    when(notes.latest(7L, null, TrendingPosts.MAX_LIMIT)).thenReturn(listed(33, 32, 31, 30));
    when(notes.latest(7L, null, 3)).thenReturn(listed(33, 32, 31));

    RemoteTrendingFeed.Page fresh = stored.latest(7L, null, null, "31", 2);
    RemoteTrendingFeed.Page none = stored.latest(7L, null, null, "33", 2);
    RemoteTrendingFeed.Page gap = stored.latest(7L, null, null, "12", 2);

    assertThat(fresh.items()).containsExactly(kept(33), kept(32));
    assertThat(fresh.next()).isNull();
    assertThat(fresh.gap()).isFalse();
    assertThat(none.items()).isEmpty();
    assertThat(none.gap()).isFalse();
    assertThat(gap.items()).containsExactly(kept(33), kept(32));
    assertThat(gap.next()).isEqualTo("r32");
    assertThat(gap.gap()).isTrue();
  }

  @Test
  void aViewerGetsWhatTheyHaveNotSeenFirstAndThenWhatTheySawBelowTheLine() {
    below(0, 3, true, "a", "b", "c");
    below(3, 3, false, "d");
    seenBefore("b", "c");

    RemoteTrendingFeed.Page first = feed.latest(null, DEVICE, null, null, 3);

    assertThat(uris(first)).containsExactly("a", "d", "b");
    assertThat(first.seenFrom()).isEqualTo(2);
    assertThat(first.next()).isEqualTo("s" + AS_OF.toEpochMilli() + ".t2");

    below(2, 3, false, "c", "d");
    RemoteTrendingFeed.Page last = feed.latest(null, DEVICE, first.next(), null, 3);

    assertThat(uris(last)).containsExactly("c");
    assertThat(last.seenFrom()).isNull();
    assertThat(last.next()).isNull();
  }

  @Test
  void theLineGoesOnThePageWhereTheFirstSeenPostLandsEvenWhenTheUnseenRanOutOnTheOneBefore() {
    below(0, 2, true, "a", "b");
    below(2, 2, false, "c", "d");
    seenBefore("b", "c");

    RemoteTrendingFeed.Page first = feed.latest(null, DEVICE, null, null, 2);
    RemoteTrendingFeed.Page second = feed.latest(null, DEVICE, first.next(), null, 2);

    assertThat(uris(first)).containsExactly("a", "d");
    assertThat(first.seenFrom()).isNull();
    assertThat(first.next()).isEqualTo("d" + AS_OF.toEpochMilli() + ".");
    assertThat(uris(second)).containsExactly("b", "c");
    assertThat(second.seenFrom()).isZero();
    assertThat(second.next()).isEqualTo("s" + AS_OF.toEpochMilli() + ".t3");
  }

  @Test
  void everyPageOfOneScrollAsksAsOfTheMillisecondOfItsFirstPageSoWhatWasSeenSinceStaysAbove() {
    below(0, 1, true, "a");
    below(1, 1, true, "b");
    seenBefore();
    when(clock.instant()).thenReturn(AS_OF.plusNanos(400_000));

    RemoteTrendingFeed.Page first = feed.latest(null, DEVICE, null, null, 1);
    when(clock.instant()).thenReturn(AS_OF.plusSeconds(60));
    RemoteTrendingFeed.Page second = feed.latest(null, DEVICE, first.next(), null, 1);

    assertThat(uris(first)).containsExactly("a");
    assertThat(uris(second)).containsExactly("b");
    verify(seen, times(2)).seenBefore(eq(DEVICE), any(), eq(AS_OF));
  }

  @Test
  void oneRequestReadsAtMostFivePagesBelowAndHandsBackWhereItStopped() {
    for (int offset = 0; offset < 14; offset += 2) {
      below(offset, 2, true, "p" + offset, "p" + (offset + 1));
    }
    seenBefore("p0", "p1", "p2", "p3", "p4", "p5", "p6", "p7", "p8", "p9", "p10", "p11");

    RemoteTrendingFeed.Page page = feed.latest(null, DEVICE, null, null, 2);

    assertThat(page.items()).isEmpty();
    assertThat(page.next()).isEqualTo("u" + AS_OF.toEpochMilli() + ".t10");
    verify(latest, times(RemoteTrendingFeed.MAX_READS)).page(eq(null), anyInt(), eq(2));

    RemoteTrendingFeed.Page after = feed.latest(null, DEVICE, page.next(), null, 2);

    assertThat(uris(after)).containsExactly("p12", "p13");
  }

  @Test
  void withoutAViewerOrWithAnOlderCursorTheLatestPagesAsBeforeAndNothingIsAskedOfTheSeen() {
    below(0, 2, true, "a", "b");
    below(2, 2, false, "c");

    RemoteTrendingFeed.Page first = feed.latest(null, null, null, null, 2);
    RemoteTrendingFeed.Page older = feed.latest(null, DEVICE, "t2", null, 2);

    assertThat(uris(first)).containsExactly("a", "b");
    assertThat(first.next()).isEqualTo("t2");
    assertThat(first.seenFrom()).isNull();
    assertThat(uris(older)).containsExactly("c");
    assertThat(older.next()).isNull();
    verifyNoInteractions(seen);
  }

  @Test
  void oncePullingIsOnAViewerPagesTheKeptNotesByIdWithTheirUris() {
    RemoteTrendingFeed stored = feed(true);
    when(notes.latest(null, null, 4)).thenReturn(listed(30, 29, 28));
    seenBefore("https://x/s/29");

    RemoteTrendingFeed.Page first = stored.latest(null, DEVICE, null, null, 3);

    assertThat(first.items()).containsExactly(kept(30), kept(28), kept(29));
    assertThat(first.seenFrom()).isEqualTo(2);
    assertThat(first.next()).isEqualTo("s" + AS_OF.toEpochMilli() + ".r29");
    verifyNoInteractions(latest);
  }
}
