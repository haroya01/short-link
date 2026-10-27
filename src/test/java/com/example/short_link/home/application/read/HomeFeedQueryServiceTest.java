package com.example.short_link.home.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.common.seen.Unseen;
import com.example.short_link.federation.application.trends.LocalSeen;
import com.example.short_link.federation.domain.SeenItem;
import com.example.short_link.federation.domain.SeenViewer;
import com.example.short_link.note.application.read.NoteQueryService;
import com.example.short_link.note.application.read.NoteSlice;
import com.example.short_link.note.application.read.NoteView;
import com.example.short_link.note.domain.NoteAuthor;
import com.example.short_link.post.application.read.BlogSlice;
import com.example.short_link.post.application.read.PostCard;
import com.example.short_link.post.application.read.PostHomeSlices;
import com.example.short_link.post.application.read.PostSlice;
import com.example.short_link.post.application.read.PublicAuthorView;
import com.example.short_link.post.application.read.PublicFeedItem;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class HomeFeedQueryServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-23T03:00:00.123456Z");

  private final NoteQueryService notes = mock(NoteQueryService.class);
  private final PostHomeSlices posts = mock(PostHomeSlices.class);
  private final LocalSeen seen = mock(LocalSeen.class);
  private final HomeForYou forYou = mock(HomeForYou.class);
  private final HomeFeedQueryService home =
      new HomeFeedQueryService(notes, posts, seen, forYou, Clock.fixed(NOW, ZoneOffset.UTC));

  private static NoteSlice.At note(int position, String at) {
    return note(position, at, position + 100L);
  }

  private static NoteSlice.At note(int position, String at, long id) {
    NoteView view = mock(NoteView.class);
    when(view.createdAt()).thenReturn(Instant.parse(at));
    when(view.id()).thenReturn(id);
    return new NoteSlice.At(position, view);
  }

  private static NoteSlice.At noteBy(int position, String at, long id, long author) {
    NoteSlice.At made = note(position, at, id);
    when(made.note().author()).thenReturn(new NoteAuthor(author, "u" + author, null));
    return made;
  }

  private static PostSlice.At postBy(int position, String at, long id, long author) {
    PostSlice.At made = post(position, at, id);
    when(made.post().item().author())
        .thenReturn(new PublicAuthorView(author, "u" + author, null, null));
    return made;
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
  void forYouIsTheOneRankedListWithoutSinceAndTheOldTabsStayAsTheyWere() {
    SeenViewer reader = SeenViewer.member(7L);
    HomeFeedQueryService.Page ranked = new HomeFeedQueryService.Page(List.of(), "r1.40");
    when(forYou.page(7L, reader, "ja", "r1.20", 40, true)).thenReturn(ranked);

    assertThat(home.page(7L, reader, "ja", "for-you", "r1.20", "3.4", 500, true)).isSameAs(ranked);
    verifyNoInteractions(notes, posts, seen);
    when(notes.everyoneAt(null, 0, 3, null)).thenReturn(new NoteSlice(List.of(), true));
    when(posts.recentAt(null, 0, 3, null)).thenReturn(new PostSlice(List.of(), true));
    home.page(null, null, "latest", null, null, 2, false);
    home.page(null, null, "nonsense", null, null, 2, false);
    verify(forYou, times(1)).page(any(), any(), any(), any(), anyInt(), any(Boolean.class));
  }

  @Test
  void blogPagesFromTheLastRowsPositionAndOnlyAFirstPageNamesTheNewest() {
    Instant top = Instant.parse("2026-10-20T10:00:00.123456Z");
    Instant last = Instant.parse("2026-10-19T10:00:00.000001Z");
    PostCard card = post(0, "2026-10-20T10:00:00Z").post();
    when(posts.blogBelow(7L, null, null, 2))
        .thenReturn(new BlogSlice(List.of(card), top, 9L, last, 4L, true));
    when(posts.blogBelow(7L, last, 4L, 2))
        .thenReturn(new BlogSlice(List.of(card), last, 3L, last, 3L, false));

    HomeFeedQueryService.Page first = home.page(7L, null, null, "blog", null, null, 2, true);
    HomeFeedQueryService.Page onward =
        home.page(7L, null, null, "blog", first.next(), null, 2, true);

    assertThat(kinds(first)).containsExactly("post");
    assertThat(first.next()).isEqualTo("b1792404000000001.4");
    assertThat(first.newest()).isEqualTo("b1792490400123456.9");
    assertThat(onward.next()).isNull();
    assertThat(onward.newest()).isNull();
    verifyNoInteractions(notes, seen, forYou);
  }

  @Test
  void aBlogMarkerIsThePublishedMicrosAndIdAndAnythingElseIsNone() {
    Instant at = Instant.parse("2026-10-20T10:00:00.123456Z");
    HomeFeedQueryService.Marker marker = new HomeFeedQueryService.Marker(at, 9L);

    assertThat(HomeFeedQueryService.Marker.parse(marker.toString())).isEqualTo(marker);
    for (String bad :
        List.of("b1.0", "b-1.2", "b1", "1.2", "bx.2", "b1.2.3", "b99999999999999999999.1", "")) {
      assertThat(HomeFeedQueryService.Marker.parse(bad)).as(bad).isNull();
    }
    assertThat(HomeFeedQueryService.Marker.parse(null)).isNull();
  }

  @Test
  void latestRunsNewestFirstAcrossNotesAndPostsAndTheCursorPicksUpWhereEachLeftOff() {
    NoteSlice.At n0 = note(0, "2026-10-20T10:00:00Z");
    NoteSlice.At n2 = note(2, "2026-10-20T08:00:00Z");
    PostSlice.At p0 = post(0, "2026-10-20T09:00:00Z");
    PostSlice.At p1 = post(1, "2026-10-20T07:00:00Z");
    when(notes.everyoneAt(null, 0, 3, null)).thenReturn(new NoteSlice(List.of(n0, n2), false));
    when(posts.recentAt(null, 0, 3, null)).thenReturn(new PostSlice(List.of(p0, p1), true));

    HomeFeedQueryService.Page page = home.page(null, null, "latest", null, null, 2, false);

    assertThat(kinds(page)).containsExactly("note", "post");
    assertThat(page.items().get(0).note()).isSameAs(n0.note());
    assertThat(page.items().get(1).post()).isSameAs(p0.post());
    assertThat(page.next()).isEqualTo("1.1");

    when(notes.everyoneAt(null, 1, 3, null)).thenReturn(new NoteSlice(List.of(n2), true));
    when(posts.recentAt(null, 1, 3, null)).thenReturn(new PostSlice(List.of(p1), true));
    HomeFeedQueryService.Page last = home.page(null, null, "latest", page.next(), null, 2, false);
    assertThat(kinds(last)).containsExactly("note", "post");
    assertThat(last.next()).isNull();
  }

  @Test
  void aRowLeftOutOfTheViewIsStillSteppedOverByTheCursor() {
    NoteSlice slice = new NoteSlice(List.of(note(1, "2026-10-20T10:00:00Z")), false);
    when(notes.everyoneAt(null, 0, 2, null)).thenReturn(slice);
    when(posts.recentAt(null, 0, 2, null)).thenReturn(new PostSlice(List.of(), true));

    assertThat(home.page(null, null, "latest", null, null, 1, false).next()).isEqualTo("2.0");
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

    assertThat(kinds(home.page(7L, null, "trending", null, null, 4, false)))
        .containsExactly("note", "post", "note", "post");
  }

  @Test
  void followingIsEmptyForSomeoneSignedOut() {
    HomeFeedQueryService.Page page = home.page(null, null, "following", null, null, 20, false);

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
    when(notes.everyoneAt(null, 0, 21, null)).thenReturn(firstNotes);
    when(posts.recentAt(null, 0, 21, null)).thenReturn(firstPosts);
    assertThat(home.page(null, null, "latest", null, null, 20, false).newest()).isEqualTo("20.7");

    NoteSlice nowNotes = new NoteSlice(List.of(newNote, seenNote), true);
    PostSlice nowPosts = new PostSlice(List.of(seenPost), true);
    when(notes.everyoneAt(null, 0, 51, null)).thenReturn(nowNotes);
    when(posts.recentAt(null, 0, 51, null)).thenReturn(nowPosts);
    HomeFeedQueryService.Page fresh = home.page(null, null, "latest", null, "20.7", 20, false);

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
    when(notes.everyoneAt(null, 0, 51, null)).thenReturn(window);
    when(posts.recentAt(null, 0, 51, null)).thenReturn(none);
    when(notes.everyoneAt(null, 0, 21, null)).thenReturn(window);
    when(posts.recentAt(null, 0, 21, null)).thenReturn(none);

    HomeFeedQueryService.Page page = home.page(null, null, "latest", null, "20.0", 20, false);

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

    HomeFeedQueryService.Page page = home.page(7L, null, "trending", null, "100.500", 20, false);

    assertThat(page.gap()).isTrue();
    assertThat(page.items()).hasSize(1);
  }

  private static final SeenViewer READER = SeenViewer.member(7L);
  private static final Instant AS_OF = Instant.parse("2026-10-23T03:00:00.123Z");
  private static final Unseen UNSEEN =
      new Unseen(SeenViewer.MEMBER, READER.key(), AS_OF, List.of(), List.of());

  @Test
  void hiddenLatestFreezesItsAsOfInTheCursorAndAsksBothSourcesToLeaveOutTheSeen() {
    NoteSlice.At n0 = note(0, "2026-10-20T10:00:00Z");
    NoteSlice.At n1 = note(1, "2026-10-20T08:00:00Z");
    PostSlice.At p0 = post(0, "2026-10-20T09:00:00Z");
    when(seen.unseen(READER, AS_OF)).thenReturn(UNSEEN);
    when(notes.everyoneAt(7L, 0, 3, UNSEEN)).thenReturn(new NoteSlice(List.of(n0, n1), true));
    when(posts.recentAt(7L, 0, 3, UNSEEN)).thenReturn(new PostSlice(List.of(p0), true));

    HomeFeedQueryService.Page page = home.page(7L, READER, "latest", null, null, 2, true);

    assertThat(page.next()).isEqualTo("h" + AS_OF.toEpochMilli() + ".1.1");
    assertThat(page.newest()).isEqualTo("100.500");

    when(notes.everyoneAt(7L, 1, 3, UNSEEN)).thenReturn(new NoteSlice(List.of(n1), true));
    when(posts.recentAt(7L, 1, 3, UNSEEN)).thenReturn(new PostSlice(List.of(), true));
    HomeFeedQueryService.Page last = home.page(7L, READER, "latest", page.next(), null, 2, false);

    assertThat(last.items()).extracting(HomeFeedQueryService.Item::note).containsExactly(n1.note());
    assertThat(last.next()).isNull();
    verify(seen, times(2)).unseen(READER, AS_OF);
  }

  @Test
  void aCursorFromBeforeHidingGoesOnWithoutIt() {
    when(notes.everyoneAt(7L, 4, 3, null)).thenReturn(new NoteSlice(List.of(), false));
    when(posts.recentAt(7L, 2, 3, null)).thenReturn(new PostSlice(List.of(), true));

    HomeFeedQueryService.Page page = home.page(7L, READER, "latest", "4.2", null, 2, true);

    assertThat(page.next()).isEqualTo("7.2");
    verifyNoInteractions(seen);
  }

  @Test
  void aBrokenHiddenCursorStartsTheHiddenListOver() {
    when(seen.unseen(READER, NOW.truncatedTo(ChronoUnit.MILLIS))).thenReturn(UNSEEN);
    when(notes.everyoneAt(7L, 0, 3, UNSEEN)).thenReturn(new NoteSlice(List.of(), true));
    when(posts.recentAt(7L, 0, 3, UNSEEN)).thenReturn(new PostSlice(List.of(), true));

    for (String cursor :
        List.of("h", "hx.1.2", "h1.2", "h1.x.2", "h-1.0.0", "h1.-1.0", "h1.0.-1")) {
      HomeFeedQueryService.Page page = home.page(7L, READER, "latest", cursor, null, 2, true);
      assertThat(page.items()).as(cursor).isEmpty();
      assertThat(page.next()).as(cursor).isNull();
    }
    verify(seen, times(7)).unseen(READER, NOW.truncatedTo(ChronoUnit.MILLIS));
  }

  @Test
  void withoutAViewerOrOutsideLatestNothingIsHidden() {
    when(notes.everyoneAt(null, 0, 3, null)).thenReturn(new NoteSlice(List.of(), true));
    when(posts.recentAt(null, 0, 3, null)).thenReturn(new PostSlice(List.of(), true));
    when(notes.trendingAt(7L, 0, 3)).thenReturn(new NoteSlice(List.of(), true));
    when(posts.trendingAt(7L, 0, 3)).thenReturn(new PostSlice(List.of(), true));

    home.page(null, null, "latest", null, null, 2, true);
    home.page(7L, READER, "trending", null, null, 2, true);

    verifyNoInteractions(seen);
  }

  @Test
  void sinceFindsTheSeenMarkerAndLeavesOutOnlyTheSeenAboveItButNotTheReadersOwn() {
    NoteSlice.At othersSeen = noteBy(0, "2026-10-20T13:00:00Z", 40L, 9L);
    NoteSlice.At ownSeen = noteBy(1, "2026-10-20T12:30:00Z", 35L, 7L);
    NoteSlice.At fresh = noteBy(2, "2026-10-20T12:00:00Z", 30L, 9L);
    NoteSlice.At marker = noteBy(3, "2026-10-20T10:00:00Z", 20L, 9L);
    PostSlice.At postSeen = postBy(0, "2026-10-20T12:20:00Z", 8L, 9L);
    PostSlice.At ownPost = postBy(1, "2026-10-20T12:10:00Z", 6L, 7L);
    PostSlice.At postMarker = postBy(2, "2026-10-20T11:00:00Z", 7L, 9L);
    when(notes.everyoneAt(7L, 0, 51, null))
        .thenReturn(new NoteSlice(List.of(othersSeen, ownSeen, fresh, marker), true));
    when(posts.recentAt(7L, 0, 51, null))
        .thenReturn(new PostSlice(List.of(postSeen, ownPost, postMarker), true));
    when(seen.seenBefore(eq(READER), any(), eq(NOW.truncatedTo(ChronoUnit.MILLIS))))
        .thenReturn(
            Set.of(
                new SeenItem(SeenItem.NOTE, 40L),
                new SeenItem(SeenItem.NOTE, 35L),
                new SeenItem(SeenItem.POST, 8L),
                new SeenItem(SeenItem.POST, 6L)));

    HomeFeedQueryService.Page page = home.page(7L, READER, "latest", null, "20.7", 20, true);

    assertThat(page.items())
        .extracting(item -> item.note() == null ? item.post() : item.note())
        .containsExactly(ownSeen.note(), ownPost.post(), fresh.note());
    assertThat(page.newest()).isEqualTo("40.8");
    assertThat(page.gap()).isFalse();
    verify(seen)
        .seenBefore(
            READER,
            List.of(
                new SeenItem(SeenItem.NOTE, 40L),
                new SeenItem(SeenItem.NOTE, 35L),
                new SeenItem(SeenItem.NOTE, 30L),
                new SeenItem(SeenItem.POST, 8L),
                new SeenItem(SeenItem.POST, 6L)),
            NOW.truncatedTo(ChronoUnit.MILLIS));
  }

  @Test
  void sinceWithNothingAboveAsksNothingOfTheSeen() {
    NoteSlice.At marker = noteBy(0, "2026-10-20T10:00:00Z", 20L, 9L);
    when(notes.everyoneAt(7L, 0, 51, null)).thenReturn(new NoteSlice(List.of(marker), true));
    when(posts.recentAt(7L, 0, 51, null)).thenReturn(new PostSlice(List.of(), true));

    HomeFeedQueryService.Page page = home.page(7L, READER, "latest", null, "20.0", 20, true);

    assertThat(page.items()).isEmpty();
    verifyNoInteractions(seen);
  }

  @Test
  void aGapWhileHidingHandsBackAHiddenFirstPage() {
    NoteSlice.At other = note(0, "2026-10-20T12:00:00Z", 99L);
    when(notes.everyoneAt(7L, 0, 51, null)).thenReturn(new NoteSlice(List.of(other), false));
    when(posts.recentAt(7L, 0, 51, null)).thenReturn(new PostSlice(List.of(), true));
    when(seen.unseen(READER, NOW.truncatedTo(ChronoUnit.MILLIS))).thenReturn(UNSEEN);
    when(notes.everyoneAt(7L, 0, 21, UNSEEN)).thenReturn(new NoteSlice(List.of(other), false));
    when(posts.recentAt(7L, 0, 21, UNSEEN)).thenReturn(new PostSlice(List.of(), true));

    HomeFeedQueryService.Page page = home.page(7L, READER, "latest", null, "20.0", 20, true);

    assertThat(page.gap()).isTrue();
    assertThat(page.next())
        .isEqualTo("h" + NOW.truncatedTo(ChronoUnit.MILLIS).toEpochMilli() + ".1.0");
  }
}
