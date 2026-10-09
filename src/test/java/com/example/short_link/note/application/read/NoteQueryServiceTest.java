package com.example.short_link.note.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.example.short_link.note.domain.NoteAuthor;
import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.NoteFeedRow;
import com.example.short_link.note.domain.repository.NoteBookmarkRepository;
import com.example.short_link.note.domain.repository.NoteLikeRepository;
import com.example.short_link.note.domain.repository.NotePeopleReader;
import com.example.short_link.note.domain.repository.NoteRepository;
import com.example.short_link.note.domain.repository.NoteRepostRepository;
import com.example.short_link.note.exception.NoteException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NoteQueryServiceTest {

  private static final NoteAuthor ME = new NoteAuthor(7L, "me", null);

  @Mock private NoteRepository notes;
  @Mock private NoteLikeRepository likes;
  @Mock private NoteRepostRepository reposts;
  @Mock private NoteBookmarkRepository bookmarks;
  @Mock private NotePeopleReader people;
  @Mock private NoteViews views;
  @InjectMocks private NoteQueryService service;

  private static NoteEntity note(Long id, Long inReplyTo) {
    NoteEntity note = new NoteEntity(7L, "n" + id, inReplyTo, null);
    ReflectionTestUtils.setField(note, "id", id);
    return note;
  }

  private static NoteView view(Long id, Long inReplyTo, Long likeCount) {
    return new NoteView(
        id, "n" + id, null, null, likeCount, null, ME, List.of(), null, inReplyTo, 0, null, null,
        null, null);
  }

  @Test
  void pagesFetchOneExtraRowToKnowIfMoreFollow() {
    List<NoteEntity> three = LongStream.of(3, 2, 1).mapToObj(id -> note(id, null)).toList();
    when(notes.topLevel(7L, 2, 3)).thenReturn(three);
    when(views.ofFeed(three.subList(0, 2), 7L))
        .thenReturn(List.of(view(3L, null, 4L), view(2L, null, 0L)));

    NoteFeedView feed = service.everyone(1, 2, 7L);

    assertThat(feed.hasNext()).isTrue();
    assertThat(feed.page()).isEqualTo(1);
    assertThat(feed.items()).extracting(NoteView::likeCount).containsExactly(4L, 0L);
  }

  @Test
  void trendingPagesLikeThePublicFeed() {
    List<NoteEntity> two = LongStream.of(5, 9).mapToObj(id -> note(id, null)).toList();
    when(notes.trending(null, 0, 21)).thenReturn(two);
    when(views.ofFeed(two, null)).thenReturn(List.of(view(5L, null, 3L), view(9L, null, 0L)));

    NoteFeedView feed = service.trending(0, 20, null);

    assertThat(feed.hasNext()).isFalse();
    assertThat(feed.items()).extracting(NoteView::id).containsExactly(5L, 9L);
    assertThat(feed.items()).extracting(NoteView::likeCount).containsExactly(3L, 0L);
  }

  @Test
  void theFeedOfOtherServersPagesLikeThePublicFeed() {
    List<NoteEntity> two = LongStream.of(7, 8).mapToObj(id -> note(id, null)).toList();
    when(notes.federated(3L, 0, 21)).thenReturn(two);
    when(views.ofFeed(two, 3L)).thenReturn(List.of(view(7L, null, 0L), view(8L, null, 1L)));

    NoteFeedView feed = service.federated(3L, 0, 20);

    assertThat(feed.hasNext()).isFalse();
    assertThat(feed.items()).extracting(NoteView::id).containsExactly(7L, 8L);
  }

  @Test
  void trendingHashtagsAreAWeekOfTagsTwoAccountsUsed() {
    when(notes.trendingTags(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.eq(7),
            org.mockito.ArgumentMatchers.eq(2),
            org.mockito.ArgumentMatchers.eq(10)))
        .thenReturn(
            List.of(
                new com.example.short_link.note.domain.TrendingTag(
                    "산책", 3, 5, List.of(0L, 0L, 1L, 0L, 2L, 1L, 1L))));

    assertThat(service.trendingTags())
        .containsExactly(new TrendingTagView("산책", 3, 5, List.of(0L, 0L, 1L, 0L, 2L, 1L, 1L)));
  }

  @Test
  void trendingLinksAreAWeekOfCardsTwoAccountsShared() {
    when(notes.trendingLinks(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.eq(7),
            org.mockito.ArgumentMatchers.eq(2),
            org.mockito.ArgumentMatchers.eq(10)))
        .thenReturn(
            List.of(
                new com.example.short_link.note.domain.TrendingLink(
                    "https://e.com", "E", null, null, 2, 3, List.of(0L, 0L, 0L, 0L, 0L, 1L, 2L))));

    assertThat(service.trendingLinks())
        .extracting(TrendingLinkView::url, TrendingLinkView::accounts)
        .containsExactly(org.assertj.core.groups.Tuple.tuple("https://e.com", 2L));
  }

  @Test
  void theNotesOfALinkPageLikeAnyFeedAndABlankLinkHasNone() {
    List<NoteEntity> two = LongStream.of(7, 8).mapToObj(id -> note(id, null)).toList();
    when(notes.linked("https://e.com", 3L, 0, 21)).thenReturn(two);
    when(views.ofFeed(two, 3L)).thenReturn(List.of(view(7L, null, 0L), view(8L, null, 1L)));

    assertThat(service.linked("https://e.com", 0, 20, 3L).items())
        .extracting(NoteView::id)
        .containsExactly(7L, 8L);
    assertThat(service.linked(" ", 0, 20, 3L).items()).isEmpty();
  }

  @Test
  void pageAndSizeAreClamped() {
    when(notes.topLevel(null, 0, NoteQueryService.MAX_PAGE_SIZE + 1)).thenReturn(List.of());
    when(views.ofFeed(List.of(), null)).thenReturn(List.of());

    NoteFeedView feed = service.everyone(-3, 10_000, null);

    assertThat(feed.page()).isZero();
    assertThat(feed.hasNext()).isFalse();
  }

  @Test
  void aProfileOfNoOneIs404() {
    when(people.activeByUsername("ghost")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.byAuthor("ghost", 0, 20, null))
        .isInstanceOf(NoteException.class);
  }

  @Test
  void profileAndFollowingReadTheRightAuthors() {
    when(people.activeByUsername("me")).thenReturn(Optional.of(ME));
    when(notes.topLevelByAuthor(7L, 9L, 0, 21)).thenReturn(List.of());
    when(views.ofFeed(anyList(), eq(9L))).thenReturn(List.of());
    service.byAuthor("me", 0, 20, 9L);

    when(people.followingIds(9L)).thenReturn(List.of(7L, 8L));
    when(notes.following(List.of(7L, 8L, 9L), 9L, 0, 21)).thenReturn(List.of());
    assertThat(service.following(9L, 0, 20).items()).isEmpty();
  }

  @Test
  void aRepostInTheFollowingFeedShowsItsReposterAndLeavesWithAGoneOne() {
    NoteEntity own = note(1L, null);
    NoteEntity reposted = note(2L, null);
    NoteEntity orphaned = note(3L, null);
    when(people.followingIds(9L)).thenReturn(List.of(8L));
    when(notes.following(List.of(8L, 9L), 9L, 0, 21))
        .thenReturn(
            List.of(
                new NoteFeedRow(reposted, 8L),
                new NoteFeedRow(orphaned, 6L),
                new NoteFeedRow(own, null)));
    when(people.activeAuthors(Set.of(8L, 6L))).thenReturn(Map.of(8L, ME));
    when(views.ofFeed(List.of(reposted, orphaned, own), 9L))
        .thenReturn(List.of(view(2L, null, null), view(3L, null, null), view(1L, null, null)));

    List<NoteView> items = service.following(9L, 0, 20).items();

    assertThat(items).extracting(NoteView::id).containsExactly(2L, 1L);
    assertThat(items.get(0).repostedBy()).isEqualTo(ME);
    assertThat(items.get(1).repostedBy()).isNull();
  }

  @Test
  void repostsKeepRepostOrderAndSkipNotesThatAreGone() {
    when(people.activeByUsername("me")).thenReturn(Optional.of(ME));
    when(reposts.recentNoteIdsByUser(7L, 0, 21)).thenReturn(List.of(5L, 9L, 2L));
    NoteEntity two = note(2L, null);
    NoteEntity five = note(5L, null);
    when(notes.findAllByIdIn(List.of(5L, 9L, 2L))).thenReturn(List.of(two, five));
    when(views.ofFeed(List.of(five, two), 9L))
        .thenReturn(List.of(view(5L, null, null), view(2L, null, null)));

    NoteFeedView feed = service.reposts("me", 0, 20, 9L);

    assertThat(feed.items()).extracting(NoteView::id).containsExactly(5L, 2L);
    assertThat(feed.hasNext()).isFalse();

    when(people.activeByUsername("ghost")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.reposts("ghost", 0, 20, null))
        .isInstanceOf(NoteException.class);
  }

  @Test
  void aThreadCarriesItsParentAndReplies() {
    NoteEntity main = note(2L, 1L);
    NoteEntity parent = note(1L, null);
    NoteEntity reply = note(3L, 2L);
    when(notes.findById(2L)).thenReturn(Optional.of(main));
    when(notes.findById(1L)).thenReturn(Optional.of(parent));
    when(notes.replies(2L, 9L, NoteQueryService.MAX_REPLIES)).thenReturn(List.of(reply));
    when(views.of(List.of(main, parent, reply), 9L))
        .thenReturn(List.of(view(2L, 1L, null), view(1L, null, null), view(3L, 2L, null)));

    NoteThreadView thread = service.thread(2L, 9L);

    assertThat(thread.note().id()).isEqualTo(2L);
    assertThat(thread.parent().id()).isEqualTo(1L);
    assertThat(thread.replies()).extracting(NoteView::id).containsExactly(3L);
  }

  @Test
  void aThreadWhoseAuthorLeftIs404AndAMissingParentIsNull() {
    NoteEntity orphan = note(2L, 1L);
    when(notes.findById(2L)).thenReturn(Optional.of(orphan));
    when(notes.findById(1L)).thenReturn(Optional.empty());
    when(notes.replies(
            org.mockito.ArgumentMatchers.eq(2L),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.eq(NoteQueryService.MAX_REPLIES)))
        .thenReturn(List.of());
    when(views.of(List.of(orphan), null)).thenReturn(List.of(view(2L, 1L, null)));
    assertThat(service.thread(2L, null).parent()).isNull();

    when(views.of(List.of(orphan), 5L)).thenReturn(List.of());
    assertThatThrownBy(() -> service.thread(2L, 5L)).isInstanceOf(NoteException.class);

    when(notes.findById(4L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.thread(4L, null)).isInstanceOf(NoteException.class);
  }

  @Test
  void likedIdsPassThrough() {
    when(likes.likedNoteIds(7L, List.of(1L))).thenReturn(List.of(1L));

    assertThat(service.likedNoteIds(7L, List.of(1L))).containsExactly(1L);
  }

  @Test
  void bookmarksComeNewestFirstAndQuotesListTheNotesThatQuoteOne() {
    NoteEntity two = note(2L, null);
    NoteEntity five = note(5L, null);
    when(bookmarks.recentNoteIdsByUser(9L, 0, 21)).thenReturn(List.of(5L, 2L));
    when(notes.findAllByIdIn(List.of(5L, 2L))).thenReturn(List.of(two, five));
    when(views.ofFeed(List.of(five, two), 9L))
        .thenReturn(List.of(view(5L, null, 0L), view(2L, null, 0L)));

    assertThat(service.bookmarks(9L, 0, 20).items())
        .extracting(NoteView::id)
        .containsExactly(5L, 2L);

    when(notes.quotesOf(7L, null, 0, 21)).thenReturn(List.of(two));
    when(views.ofFeed(List.of(two), null)).thenReturn(List.of(view(2L, null, 0L)));
    assertThat(service.quotes(7L, 0, 20, null).items())
        .extracting(NoteView::id)
        .containsExactly(2L);
  }

  @Test
  void aPostsQuotingNotesComeWithHowManyThereAre() {
    NoteEntity quoting = note(3L, null);
    when(notes.quotesOfPost(40L, 9L, 0, 21)).thenReturn(List.of(quoting));
    when(notes.countQuotesOfPost(40L, 9L)).thenReturn(1L);
    when(views.ofFeed(List.of(quoting), 9L)).thenReturn(List.of(view(3L, null, 0L)));

    PostQuotesView quotes = service.postQuotes(40L, 0, 20, 9L);

    assertThat(quotes.items()).extracting(NoteView::id).containsExactly(3L);
    assertThat(quotes.total()).isEqualTo(1L);
    assertThat(quotes.hasNext()).isFalse();
  }

  @Test
  void aSearchWithNothingToLookForAsksNothingAndOtherwisePagesTheMatches() {
    assertThat(service.search("  ", 0, 20, null).items()).isEmpty();
    assertThat(service.search("가".repeat(101), 0, 20, null).items()).isEmpty();
    org.mockito.Mockito.verifyNoInteractions(notes);

    NoteEntity hit = note(4L, null);
    when(notes.search("헥사고날", null, 7L, 0, 21)).thenReturn(List.of(hit));
    when(views.ofFeed(List.of(hit), 7L)).thenReturn(List.of(view(4L, null, 0L)));
    assertThat(service.search(" 헥사고날 ", 0, 20, 7L).items())
        .extracting(NoteView::id)
        .containsExactly(4L);

    when(notes.search(null, "%밥%", null, 0, 21)).thenReturn(List.of());
    when(views.ofFeed(List.of(), null)).thenReturn(List.of());
    assertThat(service.search("밥", 0, 20, null).items()).isEmpty();
  }
}
