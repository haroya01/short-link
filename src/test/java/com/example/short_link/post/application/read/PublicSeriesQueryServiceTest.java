package com.example.short_link.post.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostStatus;
import com.example.short_link.post.domain.SeriesActivity;
import com.example.short_link.post.domain.SeriesEntity;
import com.example.short_link.post.domain.SeriesEntry;
import com.example.short_link.post.domain.SeriesItemEntity;
import com.example.short_link.post.domain.SeriesItemType;
import com.example.short_link.post.domain.SeriesNote;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.domain.repository.SeriesItemReader;
import com.example.short_link.post.domain.repository.SeriesItemRepository;
import com.example.short_link.post.domain.repository.SeriesRepository;
import com.example.short_link.post.domain.repository.SeriesSubscriptionRepository;
import com.example.short_link.profile.exception.ProfileException;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PublicSeriesQueryServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private SeriesRepository seriesRepository;
  @Mock private PostRepository postRepository;
  @Mock private SeriesSubscriptionRepository subscriptionRepository;
  @Mock private SeriesItemRepository seriesItemRepository;
  @Mock private SeriesItemReader seriesItemReader;

  private PublicSeriesQueryService service;

  @BeforeEach
  void setUp() {
    service =
        new PublicSeriesQueryService(
            userRepository,
            seriesRepository,
            postRepository,
            subscriptionRepository,
            seriesItemRepository,
            seriesItemReader);
  }

  private PostEntity publishedPost(long userId, String slug, String title) {
    PostEntity p = new PostEntity(userId, slug, title, "ko");
    p.publish();
    return p;
  }

  private UserEntity author(String username) {
    UserEntity user = new UserEntity("u@x.com", "google", "g-1");
    user.claimUsername(username);
    return user;
  }

  private UserEntity author(long id, String username) {
    UserEntity user = new UserEntity(username + "@x.com", "google", "g-" + id);
    user.claimUsername(username);
    ReflectionTestUtils.setField(user, "id", id);
    return user;
  }

  private SeriesEntity series(long id, long userId, String slug, String title) {
    SeriesEntity s = new SeriesEntity(userId, slug, title);
    ReflectionTestUtils.setField(s, "id", id);
    return s;
  }

  private static SeriesEntry postEntry(long id, String slug, Instant at) {
    return new SeriesEntry(
        SeriesItemType.POST, id, slug, slug.toUpperCase(), "https://img/" + slug, at);
  }

  private static SeriesEntry noteEntry(long id, String excerpt, Instant at) {
    return new SeriesEntry(SeriesItemType.NOTE, id, null, excerpt, null, at);
  }

  @Test
  void discoverSeriesRanksHydratesAndDropsDeletedAuthors() {
    UserEntity alice = author(1L, "alice");
    UserEntity bob = author(2L, "bob");
    bob.softDelete();
    Instant recent = Instant.parse("2026-05-30T09:00:00Z");
    Instant mid = Instant.parse("2026-05-20T09:00:00Z");
    Instant old = Instant.parse("2026-05-10T09:00:00Z");

    when(seriesItemReader.activeSeries(2, 12))
        .thenReturn(
            List.of(
                new SeriesActivity(10L, 4, recent),
                new SeriesActivity(20L, 3, mid),
                new SeriesActivity(30L, 2, old)));
    when(seriesRepository.findAllByIdIn(any()))
        .thenReturn(
            List.of(
                series(10L, 1L, "deep-dive", "Deep Dive"),
                series(20L, 2L, "ghost", "Ghost"),
                series(30L, 1L, "side-log", "Side Log")));
    when(userRepository.findAllByIdIn(any())).thenReturn(List.of(alice, bob));
    when(seriesItemReader.readableEntries(List.of(10L, 30L)))
        .thenReturn(
            Map.of(
                10L,
                List.of(
                    postEntry(1L, "dd-1", old),
                    noteEntry(40L, "an aside", recent),
                    postEntry(2L, "dd-2", mid)),
                30L,
                List.of(noteEntry(41L, "first", old), noteEntry(42L, "second", old))));

    List<PublicSeriesCard> cards = service.discoverSeries(6);

    assertThat(cards).extracting(PublicSeriesCard::slug).containsExactly("deep-dive", "side-log");
    PublicSeriesCard first = cards.get(0);
    assertThat(first.title()).isEqualTo("Deep Dive");
    assertThat(first.postCount()).isEqualTo(2);
    assertThat(first.itemCount()).isEqualTo(3);
    assertThat(first.lastPublishedAt()).isEqualTo(recent);
    assertThat(first.author().username()).isEqualTo("alice");
    assertThat(first.posts()).extracting(SeriesPostRef::slug).containsExactly("dd-1", "dd-2");
    assertThat(first.posts().get(0).ogImageUrl()).isEqualTo("https://img/dd-1");
    assertThat(first.items())
        .containsExactly(
            new SeriesItemPreview("POST", "dd-1", null, "DD-1", "https://img/dd-1"),
            new SeriesItemPreview("NOTE", null, 40L, "an aside", null),
            new SeriesItemPreview("POST", "dd-2", null, "DD-2", "https://img/dd-2"));
    PublicSeriesCard notesOnly = cards.get(1);
    assertThat(notesOnly.postCount()).isZero();
    assertThat(notesOnly.posts()).isEmpty();
    assertThat(notesOnly.itemCount()).isEqualTo(2);
  }

  @Test
  void discoverSeriesEmptyWhenNoneActive() {
    when(seriesItemReader.activeSeries(2, 12)).thenReturn(List.of());
    assertThat(service.discoverSeries(6)).isEmpty();
  }

  @Test
  void listHidesEmptySeries() {
    UserEntity author = author("john");
    when(userRepository.findByUsername("john")).thenReturn(Optional.of(author));
    SeriesEntity withPosts = new SeriesEntity(author.getId(), "filled", "Filled");
    ReflectionTestUtils.setField(withPosts, "id", 1L);
    SeriesEntity empty = new SeriesEntity(author.getId(), "empty", "Empty");
    ReflectionTestUtils.setField(empty, "id", 2L);
    when(seriesRepository.findAllByUserIdOrderByCreatedAtDesc(author.getId()))
        .thenReturn(List.of(withPosts, empty));
    PostEntity member = new PostEntity(author.getId(), "p", "P", "ko");
    member.publish();
    ReflectionTestUtils.setField(member, "seriesId", 1L);
    when(postRepository.findAllBySeriesIdInOrderBySeriesOrderAsc(List.of(1L, 2L)))
        .thenReturn(List.of(member));

    PublicSeriesListView view = service.listPublicSeries("john");

    assertThat(view.series()).hasSize(1);
    assertThat(view.series().get(0).slug()).isEqualTo("filled");
    assertThat(view.series().get(0).postCount()).isEqualTo(1);
    assertThat(view.series().get(0).itemCount()).isEqualTo(1);
  }

  private static SeriesNote seriesNote(long id, boolean shared) {
    return new SeriesNote(
        id, 1L, "note " + id, null, Instant.parse("2026-10-09T00:00:00Z"), shared);
  }

  @Test
  void listKeepsASeriesOfNotesAloneButNotOneOfUnreadableNotes() {
    UserEntity author = author("john");
    when(userRepository.findByUsername("john")).thenReturn(Optional.of(author));
    SeriesEntity notesOnly = new SeriesEntity(author.getId(), "notes", "Notes");
    ReflectionTestUtils.setField(notesOnly, "id", 1L);
    SeriesEntity hidden = new SeriesEntity(author.getId(), "hidden", "Hidden");
    ReflectionTestUtils.setField(hidden, "id", 2L);
    when(seriesRepository.findAllByUserIdOrderByCreatedAtDesc(author.getId()))
        .thenReturn(List.of(notesOnly, hidden));
    when(postRepository.findAllBySeriesIdInOrderBySeriesOrderAsc(List.of(1L, 2L)))
        .thenReturn(List.of());
    when(seriesItemRepository.findBySeriesIdIn(List.of(1L, 2L)))
        .thenReturn(
            List.of(
                new SeriesItemEntity(1L, SeriesItemType.NOTE, 40L, 0),
                new SeriesItemEntity(1L, SeriesItemType.NOTE, 41L, 1),
                new SeriesItemEntity(2L, SeriesItemType.NOTE, 42L, 0)));
    when(seriesItemReader.notes(List.of(40L, 41L, 42L)))
        .thenReturn(
            Map.of(
                40L,
                seriesNote(40L, true),
                41L,
                seriesNote(41L, true),
                42L,
                seriesNote(42L, false)));

    PublicSeriesListView view = service.listPublicSeries("john");

    assertThat(view.series()).extracting(PublicSeriesListItem::slug).containsExactly("notes");
    assertThat(view.series().get(0).postCount()).isZero();
    assertThat(view.series().get(0).itemCount()).isEqualTo(2);
  }

  @Test
  void detailListsPublishedPostsAndReadableNotesInOneOrder() {
    UserEntity author = author(1L, "john");
    when(userRepository.findByUsername("john")).thenReturn(Optional.of(author));
    SeriesEntity series = series(42L, author.getId(), "my-series", "My Series");
    when(seriesRepository.findByUserIdAndSlug(author.getId(), "my-series"))
        .thenReturn(Optional.of(series));
    PostEntity published = publishedPost(author.getId(), "a", "A");
    ReflectionTestUtils.setField(published, "id", 10L);
    when(postRepository.findAllBySeriesIdAndStatusOrderBySeriesOrderAsc(
            series.getId(), PostStatus.PUBLISHED))
        .thenReturn(List.of(published));
    when(seriesItemRepository.findBySeriesId(42L))
        .thenReturn(
            List.of(
                new SeriesItemEntity(42L, SeriesItemType.NOTE, 40L, 0),
                new SeriesItemEntity(42L, SeriesItemType.POST, 11L, 1),
                new SeriesItemEntity(42L, SeriesItemType.POST, 10L, 2),
                new SeriesItemEntity(42L, SeriesItemType.NOTE, 41L, 3),
                new SeriesItemEntity(42L, SeriesItemType.NOTE, 43L, 4)));
    when(seriesItemReader.notes(List.of(40L, 41L, 43L)))
        .thenReturn(Map.of(40L, seriesNote(40L, true), 41L, seriesNote(41L, false)));

    PublicSeriesDetail detail = service.findPublicSeries("john", "my-series");

    assertThat(detail.items()).extracting(PublicSeriesItem::type).containsExactly("NOTE", "POST");
    assertThat(detail.items().get(0).note().id()).isEqualTo(40L);
    assertThat(detail.items().get(0).note().excerpt()).isEqualTo("note 40");
    assertThat(detail.items().get(1).post().slug()).isEqualTo("a");
    assertThat(detail.posts()).hasSize(1);
    assertThat(detail.series().postCount()).isEqualTo(1);
    assertThat(detail.series().itemCount()).isEqualTo(2);
  }

  @Test
  void detailReturnsPublishedMembers() {
    UserEntity author = author(1L, "john");
    when(userRepository.findByUsername("john")).thenReturn(Optional.of(author));
    SeriesEntity series = series(42L, author.getId(), "my-series", "My Series");
    when(seriesRepository.findByUserIdAndSlug(author.getId(), "my-series"))
        .thenReturn(Optional.of(series));
    when(postRepository.findAllBySeriesIdAndStatusOrderBySeriesOrderAsc(
            series.getId(), PostStatus.PUBLISHED))
        .thenReturn(List.of(new PostEntity(author.getId(), "a", "A", "ko")));

    PublicSeriesDetail detail = service.findPublicSeries("john", "my-series");

    assertThat(detail.series().id()).isEqualTo(42L);
    assertThat(detail.series().title()).isEqualTo("My Series");
    assertThat(detail.posts()).hasSize(1);
    assertThat(detail.posts().get(0).slug()).isEqualTo("a");
  }

  @Test
  void unknownAuthorThrows() {
    when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.listPublicSeries("ghost"))
        .isInstanceOf(ProfileException.class);
  }

  @Test
  void subscribedSeriesEmptyWhenNoSubscriptions() {
    when(subscriptionRepository.findSubscribedSeriesIds(7L)).thenReturn(List.of());
    assertThat(service.subscribedSeries(7L)).isEmpty();
  }

  @Test
  void subscribedSeriesHydratesCardsSkipsEmptyAndDeletedAuthorNewestFirst() {
    UserEntity alice = author(1L, "alice");
    UserEntity ghost = author(2L, "ghost");
    ghost.softDelete();
    when(subscriptionRepository.findSubscribedSeriesIds(7L))
        .thenReturn(List.of(10L, 20L, 30L, 40L));
    SeriesEntity s10 = series(10L, 1L, "guide", "Guide");
    SeriesEntity s20 = series(20L, 1L, "empty", "Empty");
    SeriesEntity s30 = series(30L, 2L, "gone", "Gone");
    SeriesEntity s40 = series(40L, 1L, "asides", "Asides");
    when(seriesRepository.findAllByIdIn(List.of(10L, 20L, 30L, 40L)))
        .thenReturn(List.of(s10, s20, s30, s40));
    when(userRepository.findAllByIdIn(any())).thenReturn(List.of(alice, ghost));
    Instant older = Instant.parse("2026-05-10T09:00:00Z");
    Instant newer = Instant.parse("2026-05-30T09:00:00Z");
    when(seriesItemReader.readableEntries(List.of(10L, 20L, 30L, 40L)))
        .thenReturn(
            Map.of(
                10L,
                List.of(postEntry(1L, "g1", older), postEntry(2L, "g2", older)),
                30L,
                List.of(postEntry(3L, "x", newer)),
                40L,
                List.of(noteEntry(50L, "a note", newer))));

    List<PublicSeriesCard> cards = service.subscribedSeries(7L);

    assertThat(cards).extracting(PublicSeriesCard::id).containsExactly(40L, 10L);
    assertThat(cards.get(0).postCount()).isZero();
    assertThat(cards.get(0).itemCount()).isEqualTo(1);
    assertThat(cards.get(0).lastPublishedAt()).isEqualTo(newer);
    assertThat(cards.get(1).postCount()).isEqualTo(2);
    assertThat(cards.get(1).posts()).hasSize(2);
    assertThat(cards.get(1).author().username()).isEqualTo("alice");
  }
}
