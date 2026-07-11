package com.example.short_link.post.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.domain.repository.SeriesSubscriptionRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.FollowRepository;
import com.example.short_link.user.domain.repository.UserRepository;
import java.util.List;
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
  @Mock private FollowRepository followRepository;
  @Mock private SeriesSubscriptionRepository seriesSubscriptionRepository;
  @Mock private TagPrefQueryService tagPrefQueryService;

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
            new PostFeedItemAssembler(userRepository));
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
    PostEntity p2 = post(2L, "b"); // author 2 not returned (deleted/missing) → excluded
    when(postRepository.findPublishedRecent(null, 0, 20)).thenReturn(List.of(p1, p2));
    when(userRepository.findAllByIdIn(List.of(1L, 2L))).thenReturn(List.of(user(1L, "alice")));
    when(postRepository.countPublished(null)).thenReturn(1L);

    PublicFeedView view = service.feed("recent", null, 0, 20);

    assertThat(view.items()).hasSize(1);
    assertThat(view.items().get(0).id()).isEqualTo(42L);
    assertThat(view.items().get(0).slug()).isEqualTo("a");
    assertThat(view.items().get(0).author().username()).isEqualTo("alice");
    assertThat(view.hasNext()).isFalse();
  }

  @Test
  void hasNextWhenMorePagesRemain() {
    when(postRepository.findPublishedRecent(null, 0, 2))
        .thenReturn(List.of(post(1L, "a"), post(1L, "b")));
    when(userRepository.findAllByIdIn(List.of(1L))).thenReturn(List.of(user(1L, "alice")));
    when(postRepository.countPublished(null)).thenReturn(10L);

    PublicFeedView view = service.feed("recent", null, 0, 2);

    assertThat(view.items()).hasSize(2);
    assertThat(view.hasNext()).isTrue();
  }

  @Test
  void feedByTagUsesTagQuery() {
    when(postRepository.findPublishedByTag("spring", 0, 20)).thenReturn(List.of(post(1L, "a")));
    when(userRepository.findAllByIdIn(List.of(1L))).thenReturn(List.of(user(1L, "alice")));
    when(postRepository.countPublishedByTag("spring")).thenReturn(1L);

    PublicFeedView view = service.feedByTag("spring", 0, 20);

    assertThat(view.items()).hasSize(1);
    assertThat(view.items().get(0).slug()).isEqualTo("a");
    assertThat(view.hasNext()).isFalse();
  }

  @Test
  void searchUsesRelevanceQueryByDefault() {
    when(postRepository.searchPublishedByRelevance("spring", null, 0, 20))
        .thenReturn(List.of(post(1L, "a")));
    when(userRepository.findAllByIdIn(List.of(1L))).thenReturn(List.of(user(1L, "alice")));
    when(postRepository.countSearchPublished("spring", null)).thenReturn(1L);

    // relevance = 새 기본값(sort 미인식/relevance 모두 관련성 쿼리로).
    PublicFeedView view = service.search("spring", "relevance", null, 0, 20);

    assertThat(view.items()).hasSize(1);
    assertThat(view.items().get(0).slug()).isEqualTo("a");
    verify(postRepository).searchPublishedByRelevance("spring", null, 0, 20);
  }

  @Test
  void searchUsesRecentQueryWhenSortRecent() {
    when(postRepository.searchPublished("spring", null, 0, 20)).thenReturn(List.of(post(1L, "a")));
    when(userRepository.findAllByIdIn(List.of(1L))).thenReturn(List.of(user(1L, "alice")));
    when(postRepository.countSearchPublished("spring", null)).thenReturn(1L);

    service.search("spring", "recent", null, 0, 20);

    verify(postRepository).searchPublished("spring", null, 0, 20);
  }

  @Test
  void searchUsesTrendingQueryWhenSorted() {
    when(postRepository.searchPublishedTrending("spring", null, 0, 20))
        .thenReturn(List.of(post(1L, "a")));
    when(userRepository.findAllByIdIn(List.of(1L))).thenReturn(List.of(user(1L, "alice")));
    when(postRepository.countSearchPublished("spring", null)).thenReturn(1L);

    service.search("spring", "trending", null, 0, 20);

    verify(postRepository).searchPublishedTrending("spring", null, 0, 20);
  }

  @Test
  void followingFeedMergesFollowedAuthorsAndSubscribedSeries() {
    when(followRepository.findFollowingIds(9L)).thenReturn(List.of(2L, 3L));
    when(seriesSubscriptionRepository.findSubscribedSeriesIds(9L)).thenReturn(List.of());
    when(tagPrefQueryService.get(9L)).thenReturn(new TagPrefsView(List.of(), List.of()));
    // No subscriptions / followed tags → those sides are the no-match sentinels.
    when(postRepository.findPublishedByAuthorsSeriesOrTags(
            List.of(2L, 3L), List.of(-1L), List.of("\u0000"), 0, 20))
        .thenReturn(List.of(post(2L, "a")));
    when(postRepository.countPublishedByAuthorsSeriesOrTags(
            List.of(2L, 3L), List.of(-1L), List.of("\u0000")))
        .thenReturn(1L);
    when(userRepository.findAllByIdIn(List.of(2L))).thenReturn(List.of(user(2L, "bob")));

    PublicFeedView view = service.feedFollowing(9L, 0, 20);

    assertThat(view.items()).hasSize(1);
    assertThat(view.items().get(0).author().username()).isEqualTo("bob");
  }

  @Test
  void followingFeedDrawsFromSubscribedSeriesWhenFollowingNoAuthors() {
    when(followRepository.findFollowingIds(9L)).thenReturn(List.of());
    when(seriesSubscriptionRepository.findSubscribedSeriesIds(9L)).thenReturn(List.of(7L));
    when(tagPrefQueryService.get(9L)).thenReturn(new TagPrefsView(List.of(), List.of()));
    // No followed authors/tags → those sides are no-match sentinels; series side carries the query.
    when(postRepository.findPublishedByAuthorsSeriesOrTags(
            List.of(-1L), List.of(7L), List.of("\u0000"), 0, 20))
        .thenReturn(List.of(post(2L, "a")));
    when(postRepository.countPublishedByAuthorsSeriesOrTags(
            List.of(-1L), List.of(7L), List.of("\u0000")))
        .thenReturn(1L);
    when(userRepository.findAllByIdIn(List.of(2L))).thenReturn(List.of(user(2L, "bob")));

    PublicFeedView view = service.feedFollowing(9L, 0, 20);

    assertThat(view.items()).hasSize(1);
    assertThat(view.items().get(0).author().username()).isEqualTo("bob");
  }

  @Test
  void followingFeedDrawsFromFollowedTagsWhenFollowingNoAuthorsOrSeries() {
    when(followRepository.findFollowingIds(9L)).thenReturn(List.of());
    when(seriesSubscriptionRepository.findSubscribedSeriesIds(9L)).thenReturn(List.of());
    // Followed a tag (mixed case) → it's lower-cased before hitting the (lower(t) in :tags) query.
    when(tagPrefQueryService.get(9L)).thenReturn(new TagPrefsView(List.of("Spring"), List.of()));
    when(postRepository.findPublishedByAuthorsSeriesOrTags(
            List.of(-1L), List.of(-1L), List.of("spring"), 0, 20))
        .thenReturn(List.of(post(2L, "a")));
    when(postRepository.countPublishedByAuthorsSeriesOrTags(
            List.of(-1L), List.of(-1L), List.of("spring")))
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
    when(postRepository.findPublishedTrending(null, 0, 20)).thenReturn(List.of(post(1L, "a")));
    when(userRepository.findAllByIdIn(List.of(1L))).thenReturn(List.of(user(1L, "alice")));
    when(postRepository.countPublished(null)).thenReturn(1L);

    service.feed("trending", null, 0, 20);

    verify(postRepository).findPublishedTrending(null, 0, 20);
  }

  @Test
  void trendingByTagBuildsSectionPerPopularTag() {
    when(postRepository.findPopularTags(6))
        .thenReturn(
            List.of(
                new com.example.short_link.post.domain.TagCount("spring", 3L),
                new com.example.short_link.post.domain.TagCount("rag", 2L)));
    when(postRepository.findPublishedByTag("spring", 0, 8)).thenReturn(List.of(post(1L, "a")));
    when(postRepository.findPublishedByTag("rag", 0, 8)).thenReturn(List.of(post(1L, "b")));
    when(userRepository.findAllByIdIn(List.of(1L))).thenReturn(List.of(user(1L, "alice")));

    List<TrendingTagSection> sections = service.trendingByTag(6, 8);

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
    when(postRepository.findPublishedByTag("ghost", 0, 8)).thenReturn(List.of(post(9L, "x")));
    when(userRepository.findAllByIdIn(List.of(9L))).thenReturn(List.of()); // author missing/deleted

    assertThat(service.trendingByTag(6, 8)).isEmpty();
  }

  @Test
  void recentFeedPassesLanguageFilterToRepository() {
    when(postRepository.findPublishedRecent("ko", 0, 20)).thenReturn(List.of());
    when(postRepository.countPublished("ko")).thenReturn(0L);

    service.feed("recent", "ko", 0, 20);

    verify(postRepository).findPublishedRecent("ko", 0, 20);
    verify(postRepository).countPublished("ko");
  }

  @Test
  void trendingFeedPassesLanguageFilterToRepository() {
    when(postRepository.findPublishedTrending("ja", 0, 20)).thenReturn(List.of());
    when(postRepository.countPublished("ja")).thenReturn(0L);

    service.feed("trending", "ja", 0, 20);

    verify(postRepository).findPublishedTrending("ja", 0, 20);
  }

  @Test
  void searchPassesLanguageFilterToRepository() {
    when(postRepository.searchPublished("rust", "en", 0, 20)).thenReturn(List.of());
    when(postRepository.countSearchPublished("rust", "en")).thenReturn(0L);

    service.search("rust", "recent", "en", 0, 20);

    verify(postRepository).searchPublished("rust", "en", 0, 20);
    verify(postRepository).countSearchPublished("rust", "en");
  }
}
