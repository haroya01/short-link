package com.example.short_link.home.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.note.application.read.NoteQueryService;
import com.example.short_link.note.application.read.NoteSlice;
import com.example.short_link.note.application.read.NoteView;
import com.example.short_link.post.application.read.PostSlice;
import com.example.short_link.post.application.read.PublicFeedItem;
import com.example.short_link.post.application.read.PublicFeedQueryService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class HomeFeedQueryServiceTest {

  private final NoteQueryService notes = mock(NoteQueryService.class);
  private final PublicFeedQueryService posts = mock(PublicFeedQueryService.class);
  private final HomeFeedQueryService home = new HomeFeedQueryService(notes, posts);

  private static NoteSlice.At note(int position, String at) {
    NoteView view = mock(NoteView.class);
    when(view.createdAt()).thenReturn(Instant.parse(at));
    return new NoteSlice.At(position, view);
  }

  private static PostSlice.At post(int position, String at) {
    PublicFeedItem item = mock(PublicFeedItem.class);
    when(item.publishedAt()).thenReturn(Instant.parse(at));
    return new PostSlice.At(position, item);
  }

  private static List<String> kinds(HomeFeedQueryService.Page page) {
    return page.items().stream().map(HomeFeedQueryService.Item::kind).toList();
  }

  @Test
  void latestRunsNewestFirstAcrossNotesAndPostsAndTheCursorPicksUpWhereEachLeftOff() {
    NoteSlice.At n0 = note(0, "2026-10-20T10:00:00Z");
    NoteSlice.At n2 = note(2, "2026-10-20T08:00:00Z");
    PostSlice.At p0 = post(0, "2026-10-20T09:00:00Z");
    PostSlice.At p1 = post(1, "2026-10-20T07:00:00Z");
    when(notes.everyoneAt(null, 0, 3)).thenReturn(new NoteSlice(List.of(n0, n2), false));
    when(posts.recentAt(null, 0, 3)).thenReturn(new PostSlice(List.of(p0, p1), true));

    HomeFeedQueryService.Page page = home.page(null, "latest", null, 2);

    assertThat(kinds(page)).containsExactly("note", "post");
    assertThat(page.items().get(0).note()).isSameAs(n0.note());
    assertThat(page.items().get(1).post()).isSameAs(p0.post());
    assertThat(page.next()).isEqualTo("1.1");

    when(notes.everyoneAt(null, 1, 3)).thenReturn(new NoteSlice(List.of(n2), true));
    when(posts.recentAt(null, 1, 3)).thenReturn(new PostSlice(List.of(p1), true));
    HomeFeedQueryService.Page last = home.page(null, "latest", page.next(), 2);
    assertThat(kinds(last)).containsExactly("note", "post");
    assertThat(last.next()).isNull();
  }

  @Test
  void aRowLeftOutOfTheViewIsStillSteppedOverByTheCursor() {
    NoteSlice slice = new NoteSlice(List.of(note(1, "2026-10-20T10:00:00Z")), false);
    when(notes.everyoneAt(null, 0, 2)).thenReturn(slice);
    when(posts.recentAt(null, 0, 2)).thenReturn(new PostSlice(List.of(), true));

    assertThat(home.page(null, "latest", null, 1).next()).isEqualTo("2.0");
  }

  @Test
  void trendingTakesTheTwoTrendingOrdersInTurnAndLetsTheOtherRunOn() {
    NoteSlice trendingNotes =
        new NoteSlice(
            List.of(note(0, "2026-01-01T00:00:00Z"), note(1, "2026-01-01T00:00:00Z")), true);
    PostSlice trendingPosts =
        new PostSlice(
            List.of(
                post(0, "2026-10-20T00:00:00Z"),
                post(1, "2026-10-20T00:00:00Z"),
                post(2, "2026-10-20T00:00:00Z")),
            true);
    when(notes.trendingAt(7L, 0, 5)).thenReturn(trendingNotes);
    when(posts.trendingAt(7L, 0, 5)).thenReturn(trendingPosts);

    assertThat(kinds(home.page(7L, "trending", null, 4)))
        .containsExactly("note", "post", "note", "post");
  }

  @Test
  void followingIsEmptyForSomeoneSignedOut() {
    HomeFeedQueryService.Page page = home.page(null, "following", null, 20);

    assertThat(page.items()).isEmpty();
    assertThat(page.next()).isNull();
    verifyNoInteractions(notes, posts);
  }
}
