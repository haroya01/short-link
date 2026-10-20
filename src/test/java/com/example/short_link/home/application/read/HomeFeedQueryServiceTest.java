package com.example.short_link.home.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.note.application.read.NoteQueryService;
import com.example.short_link.note.application.read.NoteSlice;
import com.example.short_link.note.application.read.NoteView;
import com.example.short_link.post.application.read.PostCard;
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
    return note(position, at, position + 100L);
  }

  private static NoteSlice.At note(int position, String at, long id) {
    NoteView view = mock(NoteView.class);
    when(view.createdAt()).thenReturn(Instant.parse(at));
    when(view.id()).thenReturn(id);
    return new NoteSlice.At(position, view);
  }

  private static PostSlice.At post(int position, String at) {
    return post(position, at, position + 500L);
  }

  private static PostSlice.At post(int position, String at, long id) {
    PublicFeedItem item = mock(PublicFeedItem.class);
    when(item.publishedAt()).thenReturn(Instant.parse(at));
    when(item.id()).thenReturn(id);
    return new PostSlice.At(position, new PostCard(item, 1, 0, null));
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

    HomeFeedQueryService.Page page = home.page(null, "latest", null, null, 2);

    assertThat(kinds(page)).containsExactly("note", "post");
    assertThat(page.items().get(0).note()).isSameAs(n0.note());
    assertThat(page.items().get(1).post()).isSameAs(p0.post());
    assertThat(page.next()).isEqualTo("1.1");

    when(notes.everyoneAt(null, 1, 3)).thenReturn(new NoteSlice(List.of(n2), true));
    when(posts.recentAt(null, 1, 3)).thenReturn(new PostSlice(List.of(p1), true));
    HomeFeedQueryService.Page last = home.page(null, "latest", page.next(), null, 2);
    assertThat(kinds(last)).containsExactly("note", "post");
    assertThat(last.next()).isNull();
  }

  @Test
  void aRowLeftOutOfTheViewIsStillSteppedOverByTheCursor() {
    NoteSlice slice = new NoteSlice(List.of(note(1, "2026-10-20T10:00:00Z")), false);
    when(notes.everyoneAt(null, 0, 2)).thenReturn(slice);
    when(posts.recentAt(null, 0, 2)).thenReturn(new PostSlice(List.of(), true));

    assertThat(home.page(null, "latest", null, null, 1).next()).isEqualTo("2.0");
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

    assertThat(kinds(home.page(7L, "trending", null, null, 4)))
        .containsExactly("note", "post", "note", "post");
  }

  @Test
  void followingIsEmptyForSomeoneSignedOut() {
    HomeFeedQueryService.Page page = home.page(null, "following", null, null, 20);

    assertThat(page.items()).isEmpty();
    assertThat(page.next()).isNull();
    verifyNoInteractions(notes, posts);
  }

  @Test
  void aFirstPageNamesTheNewestNoteAndPostAndSinceThemBringsOnlyWhatCameAbove() {
    NoteSlice.At newNote = note(0, "2026-10-20T12:00:00Z", 30L);
    NoteSlice.At seenNote = note(1, "2026-10-20T10:00:00Z", 20L);
    PostSlice.At seenPost = post(0, "2026-10-20T11:00:00Z", 7L);
    NoteSlice firstNotes = new NoteSlice(List.of(seenNote), true);
    PostSlice firstPosts = new PostSlice(List.of(seenPost), true);
    when(notes.everyoneAt(null, 0, 21)).thenReturn(firstNotes);
    when(posts.recentAt(null, 0, 21)).thenReturn(firstPosts);
    assertThat(home.page(null, "latest", null, null, 20).newest()).isEqualTo("20.7");

    NoteSlice nowNotes = new NoteSlice(List.of(newNote, seenNote), true);
    PostSlice nowPosts = new PostSlice(List.of(seenPost), true);
    when(notes.everyoneAt(null, 0, 51)).thenReturn(nowNotes);
    when(posts.recentAt(null, 0, 51)).thenReturn(nowPosts);
    HomeFeedQueryService.Page fresh = home.page(null, "latest", null, "20.7", 20);

    assertThat(fresh.items())
        .extracting(HomeFeedQueryService.Item::note)
        .containsExactly(newNote.note());
    assertThat(fresh.gap()).isFalse();
    assertThat(fresh.newest()).isEqualTo("30.7");
    assertThat(fresh.next()).isNull();
  }

  @Test
  void aMarkerOutOfReachIsAGapAndTheReaderGetsAFreshFirstPage() {
    NoteSlice.At other = note(0, "2026-10-20T12:00:00Z", 99L);
    NoteSlice window = new NoteSlice(List.of(other), false);
    PostSlice none = new PostSlice(List.of(), true);
    when(notes.everyoneAt(null, 0, 51)).thenReturn(window);
    when(posts.recentAt(null, 0, 51)).thenReturn(none);
    when(notes.everyoneAt(null, 0, 21)).thenReturn(window);
    when(posts.recentAt(null, 0, 21)).thenReturn(none);

    HomeFeedQueryService.Page page = home.page(null, "latest", null, "20.0", 20);

    assertThat(page.gap()).isTrue();
    assertThat(page.items())
        .extracting(HomeFeedQueryService.Item::note)
        .containsExactly(other.note());
    assertThat(page.newest()).isEqualTo("99.0");
  }

  @Test
  void trendingHasNothingAboveSoSinceStartsOver() {
    NoteSlice ranked = new NoteSlice(List.of(note(0, "2026-10-20T12:00:00Z")), true);
    PostSlice none = new PostSlice(List.of(), true);
    when(notes.trendingAt(7L, 0, 21)).thenReturn(ranked);
    when(posts.trendingAt(7L, 0, 21)).thenReturn(none);

    HomeFeedQueryService.Page page = home.page(7L, "trending", null, "100.500", 20);

    assertThat(page.gap()).isTrue();
    assertThat(page.items()).hasSize(1);
  }
}
