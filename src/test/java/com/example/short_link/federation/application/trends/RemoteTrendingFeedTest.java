package com.example.short_link.federation.application.trends;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.common.note.NoteViewReader;
import java.util.List;
import org.junit.jupiter.api.Test;

class RemoteTrendingFeedTest {

  private final TrendingPosts trending = mock(TrendingPosts.class);
  private final TrendingAuthors authors = mock(TrendingAuthors.class);
  private final NoteViewReader notes = mock(NoteViewReader.class);
  private final LatestPosts latest = mock(LatestPosts.class);
  private final RemoteTrendingFeed feed = new RemoteTrendingFeed(trending, latest, authors, notes);

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
                new NoteViewReader.Listed(30L, "n30"),
                new NoteViewReader.Listed(29L, "n29"),
                new NoteViewReader.Listed(28L, "n28")));
    when(notes.received(7L, 29L, 3)).thenReturn(List.of(new NoteViewReader.Listed(28L, "n28")));

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

    RemoteTrendingFeed.Page fresh = feed.latest(7L, null, "https://x/s/2", 20);
    RemoteTrendingFeed.Page none = feed.latest(7L, null, "https://x/s/3", 20);

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

    RemoteTrendingFeed.Page page = feed.latest(7L, null, "https://x/s/1", 20);

    assertThat(page.items()).containsExactly(newest);
    assertThat(page.next()).isEqualTo("t1");
    assertThat(page.gap()).isTrue();
    assertThat(feed.latest(7L, null, null, 20).gap()).isNull();
  }
}
