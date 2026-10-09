package com.example.short_link.post.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.post.domain.FollowingFeedRef;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.SeriesFeedNote;
import com.example.short_link.post.domain.SeriesItemType;
import com.example.short_link.post.domain.repository.FollowingFeedReader;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.domain.repository.SeriesItemReader;
import com.example.short_link.post.domain.repository.SeriesRepository;
import com.example.short_link.post.domain.repository.SeriesSubscriptionRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.FollowRepository;
import com.example.short_link.user.domain.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PublicFeedQueryServiceTest {

  @Mock private PostRepository postRepository;
  @Mock private UserRepository userRepository;
  @Mock private SeriesRepository seriesRepository;
  @Mock private FollowRepository followRepository;
  @Mock private SeriesSubscriptionRepository seriesSubscriptionRepository;
  @Mock private TagPrefQueryService tagPrefQueryService;
  @Mock private FollowingFeedReader followingFeedReader;
  @Mock private SeriesItemReader seriesItemReader;

  private PublicFeedQueryService service;

  @BeforeEach
  void setUp() {
    service =
        new PublicFeedQueryService(
            postRepository,
            userRepository,
            followRepository,
            seriesSubscriptionRepository,
            tagPrefQueryService,
            new PostFeedItemAssembler(userRepository, seriesRepository),
            followingFeedReader,
            seriesItemReader);
  }

  private UserEntity user(long id, String username) {
    UserEntity u = new UserEntity("u" + id + "@x.com", "google", "g-" + id);
    u.claimUsername(username);
    ReflectionTestUtils.setField(u, "id", id);
    return u;
  }

  private long nextPostId = 1;

  private PostEntity post(long userId, String slug) {
    PostEntity p = new PostEntity(userId, slug, "Title " + slug, "ko");
    // Published feed posts are always persisted, so PublicFeedItem.id is a primitive long — give
    // the
    // unpersisted test entity an id so mapping it doesn't unbox a null.
    ReflectionTestUtils.setField(p, "id", nextPostId++);
    return p;
  }

  @Test
  void recentFeedMapsAuthorsAndExcludesMissingAuthor() {
    PostEntity p1 = post(1L, "a");
    ReflectionTestUtils.setField(p1, "id", 42L);
    PostEntity p2 = post(2L, "b");
    when(postRepository.findPublishedRecent(null, null, 0, 20)).thenReturn(List.of(p1, p2));
    when(userRepository.findAllByIdIn(List.of(1L, 2L))).thenReturn(List.of(user(1L, "alice")));
    when(postRepository.countPublished(null, null)).thenReturn(1L);

    PublicFeedView view =
        service.feed(null, PublicFeedQuery.from(null, null, "recent", null, 0, 20));

    assertThat(view.items()).hasSize(1);
    assertThat(view.items().get(0).id()).isEqualTo(42L);
    assertThat(view.items().get(0).slug()).isEqualTo("a");
    assertThat(view.items().get(0).author().username()).isEqualTo("alice");
    assertThat(view.hasNext()).isFalse();
  }

  @Test
  void hasNextWhenMorePagesRemain() {
    when(postRepository.findPublishedRecent(null, null, 0, 2))
        .thenReturn(List.of(post(1L, "a"), post(1L, "b")));
    when(userRepository.findAllByIdIn(List.of(1L))).thenReturn(List.of(user(1L, "alice")));
    when(postRepository.countPublished(null, null)).thenReturn(10L);

    PublicFeedView view =
        service.feed(null, PublicFeedQuery.from(null, null, "recent", null, 0, 2));

    assertThat(view.items()).hasSize(2);
    assertThat(view.hasNext()).isTrue();
  }

  @Test
  void feedByTagUsesTagQuery() {
    when(postRepository.findPublishedByTag(null, "spring", 0, 20))
        .thenReturn(List.of(post(1L, "a")));
    when(userRepository.findAllByIdIn(List.of(1L))).thenReturn(List.of(user(1L, "alice")));
    when(postRepository.countPublishedByTag(null, "spring")).thenReturn(1L);

    PublicFeedView view =
        service.feed(null, PublicFeedQuery.from(null, "spring", null, null, 0, 20));

    assertThat(view.items()).hasSize(1);
    assertThat(view.items().get(0).slug()).isEqualTo("a");
    assertThat(view.hasNext()).isFalse();
  }

  @Test
  void searchUsesRelevanceQueryByDefault() {
    when(postRepository.searchPublishedByRelevance(null, "spring", null, 0, 20))
        .thenReturn(List.of(post(1L, "a")));
    when(userRepository.findAllByIdIn(List.of(1L))).thenReturn(List.of(user(1L, "alice")));
    when(postRepository.countSearchPublished(null, "spring", null)).thenReturn(1L);

    PublicFeedView view =
        service.feed(null, PublicFeedQuery.from("spring", null, "relevance", null, 0, 20));

    assertThat(view.items()).hasSize(1);
    assertThat(view.items().get(0).slug()).isEqualTo("a");
    verify(postRepository).searchPublishedByRelevance(null, "spring", null, 0, 20);
  }

  @Test
  void searchUsesRecentQueryWhenSortRecent() {
    when(postRepository.searchPublished(null, "spring", null, 0, 20))
        .thenReturn(List.of(post(1L, "a")));
    when(userRepository.findAllByIdIn(List.of(1L))).thenReturn(List.of(user(1L, "alice")));
    when(postRepository.countSearchPublished(null, "spring", null)).thenReturn(1L);

    service.feed(null, PublicFeedQuery.from("spring", null, "recent", null, 0, 20));

    verify(postRepository).searchPublished(null, "spring", null, 0, 20);
  }

  @Test
  void searchUsesTrendingQueryWhenSorted() {
    when(postRepository.searchPublishedTrending(null, "spring", null, 0, 20))
        .thenReturn(List.of(post(1L, "a")));
    when(userRepository.findAllByIdIn(List.of(1L))).thenReturn(List.of(user(1L, "alice")));
    when(postRepository.countSearchPublished(null, "spring", null)).thenReturn(1L);

    service.feed(null, PublicFeedQuery.from("spring", null, "trending", null, 0, 20));

    verify(postRepository).searchPublishedTrending(null, "spring", null, 0, 20);
  }

  @Test
  void combinedSearchAndTagUsesOnlyTheSearchRepositoryQueries() {
    when(postRepository.searchPublishedTrending(null, "spring", " ko ", 2, 8))
        .thenReturn(List.of());

    service.feed(null, PublicFeedQuery.from(" spring ", "java", "TRENDING", " ko ", 2, 8));

    verify(postRepository).searchPublishedTrending(null, "spring", " ko ", 2, 8);
    verify(postRepository).countSearchPublished(null, "spring", " ko ");
    verifyNoMoreInteractions(postRepository);
  }

  @Test
  void taggedFeedHonorsTrendingSortIgnoresLanguageAndCountsTheSameTag() {
    when(postRepository.findPublishedTrendingByTag(null, "java", 0, 20)).thenReturn(List.of());

    service.feed(null, PublicFeedQuery.from("  ", " java ", "trending", "ja", 0, 20));

    verify(postRepository).findPublishedTrendingByTag(null, "java", 0, 20);
    verify(postRepository).countPublishedByTag(null, "java");
    verifyNoMoreInteractions(postRepository);
  }

  @Test
  void taggedFeedWithoutTrendingSortStaysNewestFirst() {
    when(postRepository.findPublishedByTag(null, "java", 0, 20)).thenReturn(List.of());

    service.feed(null, PublicFeedQuery.from(null, "java", "recent", null, 0, 20));

    verify(postRepository).findPublishedByTag(null, "java", 0, 20);
    verify(postRepository).countPublishedByTag(null, "java");
    verifyNoMoreInteractions(postRepository);
  }

  @Test
  void aSignedInViewerScopesEveryPublicFeedRead() {
    service.feed(7L, PublicFeedQuery.from(null, null, "recent", null, 0, 20));
    service.feed(7L, PublicFeedQuery.from(null, null, "trending", null, 0, 20));
    service.feed(7L, PublicFeedQuery.from(null, "java", "recent", null, 0, 20));
    service.feed(7L, PublicFeedQuery.from(null, "java", "trending", null, 0, 20));
    service.feed(7L, PublicFeedQuery.from("kotlin", null, "relevance", null, 0, 20));
    service.feed(7L, PublicFeedQuery.from("kotlin", null, "recent", null, 0, 20));
    service.feed(7L, PublicFeedQuery.from("kotlin", null, "trending", null, 0, 20));

    verify(postRepository).findPublishedRecent(7L, null, 0, 20);
    verify(postRepository).findPublishedTrending(7L, null, 0, 20);
    verify(postRepository, org.mockito.Mockito.times(2)).countPublished(7L, null);
    verify(postRepository).findPublishedByTag(7L, "java", 0, 20);
    verify(postRepository).findPublishedTrendingByTag(7L, "java", 0, 20);
    verify(postRepository, org.mockito.Mockito.times(2)).countPublishedByTag(7L, "java");
    verify(postRepository).searchPublishedByRelevance(7L, "kotlin", null, 0, 20);
    verify(postRepository).searchPublished(7L, "kotlin", null, 0, 20);
    verify(postRepository).searchPublishedTrending(7L, "kotlin", null, 0, 20);
    verify(postRepository, org.mockito.Mockito.times(3)).countSearchPublished(7L, "kotlin", null);
    verifyNoMoreInteractions(postRepository);
  }

  @Test
  void suggestedAuthorsAndTopicSectionsAreScopedToTheViewer() {
    when(postRepository.findPopularTags(6))
        .thenReturn(List.of(new com.example.short_link.post.domain.TagCount("spring", 3L)));

    service.suggestedAuthors(7L, 5);
    service.trendingByTag(7L, 6, 8);

    verify(postRepository).findTopAuthorStats(7L, 10);
    verify(postRepository).findPublishedTrendingByTag(7L, "spring", 0, 8);
  }

  @Test
  void followingFeedMergesFollowedAuthorsAndSubscribedSeries() {
    when(followRepository.findFollowingIds(9L)).thenReturn(List.of(2L, 3L));
    when(seriesSubscriptionRepository.findSubscribedSeriesIds(9L)).thenReturn(List.of());
    when(tagPrefQueryService.get(9L)).thenReturn(new TagPrefsView(List.of(), List.of()));
    when(postRepository.findPublishedByAuthorsSeriesOrTags(
            9L, List.of(2L, 3L), List.of(), List.of(), 0, 20))
        .thenReturn(List.of(post(2L, "a")));
    when(postRepository.countPublishedByAuthorsSeriesOrTags(
            9L, List.of(2L, 3L), List.of(), List.of()))
        .thenReturn(1L);
    when(userRepository.findAllByIdIn(List.of(2L))).thenReturn(List.of(user(2L, "bob")));

    PublicFeedView view = service.feedFollowing(9L, 0, 20);

    assertThat(view.items()).hasSize(1);
    assertThat(view.items().get(0).author().username()).isEqualTo("bob");
  }

  @Test
  void aSubscribedSeriesCutsThePageFromPostsAndItsNotesTogether() {
    when(followRepository.findFollowingIds(9L)).thenReturn(List.of(3L));
    when(seriesSubscriptionRepository.findSubscribedSeriesIds(9L)).thenReturn(List.of(7L));
    when(tagPrefQueryService.get(9L)).thenReturn(new TagPrefsView(List.of(), List.of()));
    PostEntity inSeries = post(2L, "a");
    inSeries.assignToSeries(7L, 0);
    PostEntity byFollowed = post(3L, "b");
    when(followingFeedReader.page(9L, List.of(3L), List.of(7L), List.of(), 20, 20))
        .thenReturn(
            List.of(
                new FollowingFeedRef(SeriesItemType.NOTE, 40L, 7L),
                new FollowingFeedRef(SeriesItemType.POST, byFollowed.getId(), null),
                new FollowingFeedRef(SeriesItemType.NOTE, 41L, 7L),
                new FollowingFeedRef(SeriesItemType.NOTE, 42L, 7L),
                new FollowingFeedRef(SeriesItemType.POST, inSeries.getId(), null)));
    when(followingFeedReader.count(9L, List.of(3L), List.of(7L), List.of())).thenReturn(45L);
    when(postRepository.findAllByIdIn(List.of(byFollowed.getId(), inSeries.getId())))
        .thenReturn(List.of(inSeries, byFollowed));
    Instant at = Instant.parse("2026-10-09T00:00:00Z");
    when(seriesItemReader.feedNotes(List.of(40L, 41L, 42L)))
        .thenReturn(
            Map.of(
                40L,
                new SeriesFeedNote(
                    40L,
                    "body",
                    "spoilers",
                    at,
                    new SeriesFeedNote.Author(2L, "bob", null, null, "Bob"),
                    7L,
                    "s7",
                    "Series 7")));
    when(userRepository.findAllByIdIn(List.of(3L, 2L)))
        .thenReturn(List.of(user(2L, "bob"), user(3L, "carol")));

    PublicFeedView view = service.feedFollowing(9L, 1, 20);

    assertThat(view.items()).extracting(PublicFeedItem::slug).containsExactly("b", "a");
    assertThat(view.items().get(0).followReason()).isEqualTo(FollowReason.author());
    assertThat(view.items().get(1).followReason()).isEqualTo(FollowReason.series());
    assertThat(view.hasNext()).isTrue();
    assertThat(view.seriesNotes()).hasSize(1);
    FeedSeriesNote note = view.seriesNotes().get(0);
    assertThat(note.id()).isEqualTo(40L);
    assertThat(note.author().username()).isEqualTo("bob");
    assertThat(note.author().displayName()).isEqualTo("Bob");
    assertThat(note.excerpt()).isEqualTo("spoilers");
    assertThat(note.series()).isEqualTo(new FeedSeriesNote.SeriesRef(7L, "s7", "Series 7"));
    verify(postRepository, never())
        .findPublishedByAuthorsSeriesOrTags(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyInt(),
            org.mockito.ArgumentMatchers.anyInt());
  }

  @Test
  void aPageOfOnlySeriesNotesReadsNoPosts() {
    when(followRepository.findFollowingIds(9L)).thenReturn(List.of());
    when(seriesSubscriptionRepository.findSubscribedSeriesIds(9L)).thenReturn(List.of(7L));
    when(tagPrefQueryService.get(9L)).thenReturn(new TagPrefsView(List.of(), List.of()));
    when(followingFeedReader.page(9L, List.of(), List.of(7L), List.of(), 0, 20))
        .thenReturn(List.of());
    when(followingFeedReader.count(9L, List.of(), List.of(7L), List.of())).thenReturn(0L);

    PublicFeedView view = service.feedFollowing(9L, 0, 20);

    assertThat(view.items()).isEmpty();
    assertThat(view.seriesNotes()).isEmpty();
    assertThat(view.hasNext()).isFalse();
    verify(postRepository, never()).findAllByIdIn(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void followingFeedDrawsFromFollowedTagsWhenFollowingNoAuthorsOrSeries() {
    when(followRepository.findFollowingIds(9L)).thenReturn(List.of());
    when(seriesSubscriptionRepository.findSubscribedSeriesIds(9L)).thenReturn(List.of());
    when(tagPrefQueryService.get(9L)).thenReturn(new TagPrefsView(List.of("Spring"), List.of()));
    when(postRepository.findPublishedByAuthorsSeriesOrTags(
            9L, List.of(), List.of(), List.of("spring"), 0, 20))
        .thenReturn(List.of(post(2L, "a")));
    when(postRepository.countPublishedByAuthorsSeriesOrTags(
            9L, List.of(), List.of(), List.of("spring")))
        .thenReturn(1L);
    when(userRepository.findAllByIdIn(List.of(2L))).thenReturn(List.of(user(2L, "bob")));

    PublicFeedView view = service.feedFollowing(9L, 0, 20);

    assertThat(view.items()).hasSize(1);
    assertThat(view.items().get(0).author().username()).isEqualTo("bob");
  }

  @Test
  void followingFeedIsEmptyWhenUserFollowsAndSubscribesNothing() {
    when(followRepository.findFollowingIds(9L)).thenReturn(List.of());
    when(seriesSubscriptionRepository.findSubscribedSeriesIds(9L)).thenReturn(List.of());
    when(tagPrefQueryService.get(9L)).thenReturn(new TagPrefsView(List.of(), List.of()));

    PublicFeedView view = service.feedFollowing(9L, 0, 20);

    assertThat(view.items()).isEmpty();
    assertThat(view.hasNext()).isFalse();
  }

  @Test
  void popularTagsDelegatesToRepository() {
    when(postRepository.findPopularTags(50))
        .thenReturn(
            List.of(
                new com.example.short_link.post.domain.TagCount("spring", 7L),
                new com.example.short_link.post.domain.TagCount("react", 3L)));

    var tags = service.popularTags(50);

    assertThat(tags).hasSize(2);
    assertThat(tags.get(0).tag()).isEqualTo("spring");
    assertThat(tags.get(0).count()).isEqualTo(7);
  }

  @Test
  void trendingUsesTrendingQuery() {
    when(postRepository.findPublishedTrending(null, null, 0, 20))
        .thenReturn(List.of(post(1L, "a")));
    when(userRepository.findAllByIdIn(List.of(1L))).thenReturn(List.of(user(1L, "alice")));
    when(postRepository.countPublished(null, null)).thenReturn(1L);

    service.feed(null, PublicFeedQuery.from(null, null, "trending", null, 0, 20));

    verify(postRepository).findPublishedTrending(null, null, 0, 20);
  }

  @Test
  void trendingByTagBuildsSectionPerPopularTag() {
    when(postRepository.findPopularTags(6))
        .thenReturn(
            List.of(
                new com.example.short_link.post.domain.TagCount("spring", 3L),
                new com.example.short_link.post.domain.TagCount("rag", 2L)));
    when(postRepository.findPublishedTrendingByTag(null, "spring", 0, 8))
        .thenReturn(List.of(post(1L, "a")));
    when(postRepository.findPublishedTrendingByTag(null, "rag", 0, 8))
        .thenReturn(List.of(post(1L, "b")));
    when(userRepository.findAllByIdIn(List.of(1L))).thenReturn(List.of(user(1L, "alice")));

    List<TrendingTagSection> sections = service.trendingByTag(null, 6, 8);

    assertThat(sections).hasSize(2);
    assertThat(sections.get(0).tag()).isEqualTo("spring");
    assertThat(sections.get(0).postCount()).isEqualTo(3);
    assertThat(sections.get(0).posts()).hasSize(1);
    assertThat(sections.get(0).posts().get(0).slug()).isEqualTo("a");
  }

  @Test
  void trendingByTagSkipsTagsWhoseAuthorsAreAllMissing() {
    when(postRepository.findPopularTags(6))
        .thenReturn(List.of(new com.example.short_link.post.domain.TagCount("ghost", 1L)));
    when(postRepository.findPublishedTrendingByTag(null, "ghost", 0, 8))
        .thenReturn(List.of(post(9L, "x")));
    when(userRepository.findAllByIdIn(List.of(9L))).thenReturn(List.of());

    assertThat(service.trendingByTag(null, 6, 8)).isEmpty();
  }

  @Test
  void recentFeedPassesLanguageFilterToRepository() {
    when(postRepository.findPublishedRecent(null, "ko", 0, 20)).thenReturn(List.of());
    when(postRepository.countPublished(null, "ko")).thenReturn(0L);

    service.feed(null, PublicFeedQuery.from(null, null, "recent", "ko", 0, 20));

    verify(postRepository).findPublishedRecent(null, "ko", 0, 20);
    verify(postRepository).countPublished(null, "ko");
  }

  @Test
  void trendingFeedPassesLanguageFilterToRepository() {
    when(postRepository.findPublishedTrending(null, "ja", 0, 20)).thenReturn(List.of());
    when(postRepository.countPublished(null, "ja")).thenReturn(0L);

    service.feed(null, PublicFeedQuery.from(null, null, "trending", "ja", 0, 20));

    verify(postRepository).findPublishedTrending(null, "ja", 0, 20);
  }

  @Test
  void searchPassesLanguageFilterToRepository() {
    when(postRepository.searchPublished(null, "rust", "en", 0, 20)).thenReturn(List.of());
    when(postRepository.countSearchPublished(null, "rust", "en")).thenReturn(0L);

    service.feed(null, PublicFeedQuery.from("rust", null, "recent", "en", 0, 20));

    verify(postRepository).searchPublished(null, "rust", "en", 0, 20);
    verify(postRepository).countSearchPublished(null, "rust", "en");
  }
}
