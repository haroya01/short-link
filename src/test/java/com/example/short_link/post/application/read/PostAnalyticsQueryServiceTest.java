package com.example.short_link.post.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.example.short_link.post.domain.DailyViewCount;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.domain.repository.PostViewEventRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PostAnalyticsQueryServiceTest {

  private static final long USER = 7L;
  private static final long OTHER = 99L;
  // Frozen "today" = 2026-06-01 (UTC).
  private final Clock clock = Clock.fixed(Instant.parse("2026-06-01T10:00:00Z"), ZoneOffset.UTC);

  @Mock private PostRepository postRepository;
  @Mock private PostViewEventRepository viewEventRepository;
  @Mock private com.example.short_link.post.domain.repository.PostLinkClickReader linkClickReader;
  @Mock private com.example.short_link.post.domain.repository.PostFollowReader followReader;
  @Mock private com.example.short_link.post.domain.repository.SeriesRepository seriesRepository;

  @Mock
  private com.example.short_link.post.domain.repository.SeriesSubscriptionRepository
      subscriptionRepository;

  private PostAnalyticsQueryService service;

  @BeforeEach
  void setUp() {
    service =
        new PostAnalyticsQueryService(
            postRepository,
            viewEventRepository,
            linkClickReader,
            followReader,
            seriesRepository,
            subscriptionRepository,
            clock);
  }

  private static PostEntity post(long owner, String slug, int views, int likes) {
    PostEntity p = new PostEntity(owner, slug, "Title " + slug, "ko");
    for (int i = 0; i < views; i++) {
      p.incrementViewCount();
    }
    for (int i = 0; i < likes; i++) {
      p.incrementLikeCount();
    }
    return p;
  }

  @Test
  void postAnalytics_fillsWindowAndSumsViews() {
    PostEntity p = post(USER, "hello", 12, 4);
    when(postRepository.findById(1L)).thenReturn(Optional.of(p));
    // Sparse: two days inside a 7-day window.
    when(viewEventRepository.countDailyByPostIdSince(eqId(1L), org.mockito.ArgumentMatchers.any()))
        .thenReturn(
            List.of(
                new DailyViewCount(LocalDate.parse("2026-05-30"), 2),
                new DailyViewCount(LocalDate.parse("2026-06-01"), 5)));
    when(linkClickReader.countByPostId(1L)).thenReturn(25L);
    when(linkClickReader.countByPostIdSince(eqId(1L), org.mockito.ArgumentMatchers.any()))
        .thenReturn(7L);
    when(followReader.countBySourcePostId(1L)).thenReturn(3L);
    when(followReader.countBySourcePostIdSince(eqId(1L), org.mockito.ArgumentMatchers.any()))
        .thenReturn(1L);
    when(linkClickReader.breakdownByPostId(eqId(1L), org.mockito.ArgumentMatchers.anyInt()))
        .thenReturn(
            List.of(
                new com.example.short_link.post.domain.PostLinkClick("abc", "https://x.com", 9)));

    PostAnalyticsView view = service.postAnalytics(USER, 1L, 7);

    assertThat(view.slug()).isEqualTo("hello");
    assertThat(view.lifetimeViews()).isEqualTo(12);
    assertThat(view.lifetimeLikes()).isEqualTo(4);
    assertThat(view.windowDays()).isEqualTo(7);
    assertThat(view.daily()).hasSize(7); // 2026-05-26 .. 2026-06-01 inclusive
    assertThat(view.daily().get(0).date()).isEqualTo(LocalDate.parse("2026-05-26"));
    assertThat(view.daily().get(6).date()).isEqualTo(LocalDate.parse("2026-06-01"));
    assertThat(view.windowViews()).isEqualTo(7); // 2 + 5, gaps filled with 0
    assertThat(view.daily().get(6).views()).isEqualTo(5);
    assertThat(view.lifetimeLinkClicks()).isEqualTo(25);
    assertThat(view.windowLinkClicks()).isEqualTo(7);
    assertThat(view.lifetimeFollows()).isEqualTo(3);
    assertThat(view.windowFollows()).isEqualTo(1);
    assertThat(view.linkBreakdown())
        .singleElement()
        .satisfies(
            lc -> {
              assertThat(lc.shortCode()).isEqualTo("abc");
              assertThat(lc.clicks()).isEqualTo(9);
            });
  }

  @Test
  void postAnalytics_allTimeWhenNonPositiveSpansFromFirstActivity() {
    PostEntity p = post(USER, "hello", 0, 0);
    when(postRepository.findById(1L)).thenReturn(Optional.of(p));
    // days <= 0 = 전체(all-time): the window spans from the earliest recorded day, not a fixed 30.
    when(viewEventRepository.countDailyByPostIdSince(eqId(1L), org.mockito.ArgumentMatchers.any()))
        .thenReturn(List.of(new DailyViewCount(LocalDate.parse("2026-05-25"), 4)));

    PostAnalyticsView view = service.postAnalytics(USER, 1L, 0);

    // Frozen today = 2026-06-01; earliest data 2026-05-25 → 8 days inclusive.
    assertThat(view.windowDays()).isEqualTo(8);
    assertThat(view.daily()).hasSize(8);
    assertThat(view.windowViews()).isEqualTo(4);
  }

  @Test
  void postAnalytics_rejectsNonOwner() {
    when(postRepository.findById(1L)).thenReturn(Optional.of(post(OTHER, "hers", 1, 0)));

    assertThatThrownBy(() -> service.postAnalytics(USER, 1L, 7))
        .isInstanceOfSatisfying(
            PostException.class,
            e -> assertThat(e.errorCode()).isEqualTo(PostErrorCode.PERMISSION_DENIED));
  }

  @Test
  void postAnalytics_notFound() {
    when(postRepository.findById(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.postAnalytics(USER, 1L, 7))
        .isInstanceOfSatisfying(
            PostException.class,
            e -> assertThat(e.errorCode()).isEqualTo(PostErrorCode.POST_NOT_FOUND));
  }

  @Test
  void overview_aggregatesTotals() {
    PostEntity a = post(USER, "a", 100, 10);
    a.publish();
    PostEntity b = post(USER, "b", 5, 1); // draft
    PostEntity c = post(USER, "c", 50, 3);
    c.publish();
    when(postRepository.findAllByUserIdOrderByCreatedAtDesc(USER)).thenReturn(List.of(a, b, c));
    when(viewEventRepository.countDailyByUserIdSince(
            org.mockito.ArgumentMatchers.eq(USER), org.mockito.ArgumentMatchers.any()))
        .thenReturn(List.of(new DailyViewCount(LocalDate.parse("2026-06-01"), 9)));
    when(linkClickReader.countByUserId(USER)).thenReturn(40L);
    when(linkClickReader.countByUserIdSince(
            org.mockito.ArgumentMatchers.eq(USER), org.mockito.ArgumentMatchers.any()))
        .thenReturn(6L);
    when(followReader.countByUserId(USER)).thenReturn(8L);
    when(followReader.countByUserIdSince(
            org.mockito.ArgumentMatchers.eq(USER), org.mockito.ArgumentMatchers.any()))
        .thenReturn(2L);

    AuthorAnalyticsOverview o = service.overview(USER, 30);

    assertThat(o.totalPosts()).isEqualTo(3);
    assertThat(o.publishedPosts()).isEqualTo(2);
    assertThat(o.lifetimeViews()).isEqualTo(155);
    assertThat(o.lifetimeLikes()).isEqualTo(14);
    assertThat(o.windowViews()).isEqualTo(9);
    assertThat(o.lifetimeLinkClicks()).isEqualTo(40);
    assertThat(o.windowLinkClicks()).isEqualTo(6);
    assertThat(o.lifetimeFollows()).isEqualTo(8);
    assertThat(o.windowFollows()).isEqualTo(2);
    assertThat(o.daily()).hasSize(30);
  }

  @Test
  void postPerformance_mapsRowsWithFollowsAndComputesHasNext() {
    PostEntity a = post(USER, "a", 100, 10);
    org.springframework.test.util.ReflectionTestUtils.setField(a, "id", 1L);
    PostEntity c = post(USER, "c", 50, 3);
    org.springframework.test.util.ReflectionTestUtils.setField(c, "id", 3L);
    when(postRepository.findUserAnalyticsPosts(
            USER, 0, 2, com.example.short_link.post.domain.PostPerformanceSort.VIEWS))
        .thenReturn(List.of(a, c));
    when(followReader.countBySourcePostIdIn(org.mockito.ArgumentMatchers.any()))
        .thenReturn(java.util.Map.of(1L, 5L, 3L, 1L));
    when(postRepository.countUserAnalyticsPosts(USER)).thenReturn(5L);

    PostPerformanceResult pageResult =
        service.postPerformance(
            USER, 0, 2, com.example.short_link.post.domain.PostPerformanceSort.VIEWS);

    assertThat(pageResult.items()).extracting(TopPostView::slug).containsExactly("a", "c");
    assertThat(pageResult.items()).extracting(TopPostView::followsGained).containsExactly(5L, 1L);
    assertThat(pageResult.page()).isZero();
    assertThat(pageResult.hasNext()).isTrue(); // (0+1)*2 = 2 < 5
  }

  @Test
  void postPerformance_lastPageHasNoNext() {
    when(postRepository.findUserAnalyticsPosts(
            USER, 1, 2, com.example.short_link.post.domain.PostPerformanceSort.LIKES))
        .thenReturn(List.of());
    when(followReader.countBySourcePostIdIn(org.mockito.ArgumentMatchers.any()))
        .thenReturn(java.util.Map.of());
    when(postRepository.countUserAnalyticsPosts(USER)).thenReturn(4L);

    PostPerformanceResult pageResult =
        service.postPerformance(
            USER, 1, 2, com.example.short_link.post.domain.PostPerformanceSort.LIKES);

    assertThat(pageResult.hasNext()).isFalse(); // (1+1)*2 = 4, not < 4
  }

  @Test
  void overview_allTimeSpansFromFirstActivity() {
    PostEntity a = post(USER, "a", 10, 1);
    a.publish();
    when(postRepository.findAllByUserIdOrderByCreatedAtDesc(USER)).thenReturn(List.of(a));
    when(viewEventRepository.countDailyByUserIdSince(
            org.mockito.ArgumentMatchers.eq(USER), org.mockito.ArgumentMatchers.any()))
        .thenReturn(
            List.of(
                new DailyViewCount(LocalDate.parse("2026-05-20"), 3),
                new DailyViewCount(LocalDate.parse("2026-06-01"), 4)));
    when(linkClickReader.countByUserId(USER)).thenReturn(0L);
    when(linkClickReader.countByUserIdSince(
            org.mockito.ArgumentMatchers.eq(USER), org.mockito.ArgumentMatchers.any()))
        .thenReturn(40L);
    when(followReader.countByUserId(USER)).thenReturn(0L);
    when(followReader.countByUserIdSince(
            org.mockito.ArgumentMatchers.eq(USER), org.mockito.ArgumentMatchers.any()))
        .thenReturn(8L);

    AuthorAnalyticsOverview o = service.overview(USER, 0); // 0 = 전체(all-time)

    // Frozen today = 2026-06-01; earliest data 2026-05-20 → 13 days inclusive, spanning the chart.
    assertThat(o.windowDays()).isEqualTo(13);
    assertThat(o.daily()).hasSize(13);
    assertThat(o.windowViews()).isEqualTo(7); // 3 + 4
    // windowed link/follow counts use since=EPOCH for all-time → equal the lifetime totals.
    assertThat(o.windowLinkClicks()).isEqualTo(40);
    assertThat(o.windowFollows()).isEqualTo(8);
  }

  @Test
  void seriesAnalytics_aggregatesSubscribersAndTraction() {
    com.example.short_link.post.domain.SeriesEntity s =
        new com.example.short_link.post.domain.SeriesEntity(USER, "s1", "My Series");
    org.springframework.test.util.ReflectionTestUtils.setField(s, "id", 9L);
    when(seriesRepository.findAllByUserIdOrderByCreatedAtDesc(USER)).thenReturn(List.of(s));
    PostEntity a = post(USER, "a", 100, 5);
    PostEntity b = post(USER, "b", 50, 3);
    org.springframework.test.util.ReflectionTestUtils.setField(a, "seriesId", 9L);
    org.springframework.test.util.ReflectionTestUtils.setField(b, "seriesId", 9L);
    when(postRepository.findAllBySeriesIdInOrderBySeriesOrderAsc(List.of(9L)))
        .thenReturn(List.of(a, b));
    when(subscriptionRepository.countBySeriesIdIn(List.of(9L))).thenReturn(Map.of(9L, 7L));

    assertThat(service.seriesAnalytics(USER))
        .singleElement()
        .satisfies(
            r -> {
              assertThat(r.title()).isEqualTo("My Series");
              assertThat(r.postCount()).isEqualTo(2);
              assertThat(r.subscriberCount()).isEqualTo(7);
              assertThat(r.totalViews()).isEqualTo(150);
              assertThat(r.totalLikes()).isEqualTo(8);
            });
  }

  @Test
  void seriesDetail_buildsCumulativeSubscriberTrend() {
    com.example.short_link.post.domain.SeriesEntity s =
        new com.example.short_link.post.domain.SeriesEntity(USER, "s1", "My Series");
    org.springframework.test.util.ReflectionTestUtils.setField(s, "id", 9L);
    when(seriesRepository.findById(9L)).thenReturn(Optional.of(s));
    when(postRepository.findAllBySeriesIdOrderBySeriesOrderAsc(9L))
        .thenReturn(List.of(post(USER, "a", 100, 5)));
    when(subscriptionRepository.countBySeriesId(9L)).thenReturn(3L);
    when(subscriptionRepository.countDailyBySeriesIdSince(
            eqId(9L), org.mockito.ArgumentMatchers.any()))
        .thenReturn(
            List.of(
                new DailyViewCount(LocalDate.parse("2026-05-30"), 2),
                new DailyViewCount(LocalDate.parse("2026-06-01"), 1)));

    SeriesAnalyticsDetail d = service.seriesDetail(USER, 9L, 7);

    assertThat(d.series().title()).isEqualTo("My Series");
    assertThat(d.series().subscriberCount()).isEqualTo(3);
    assertThat(d.windowDays()).isEqualTo(7);
    assertThat(d.subscriberDaily()).hasSize(7); // 2026-05-26 .. 2026-06-01
    // Cumulative running total: 0,0,0,0,2(05-30),2,3(06-01).
    assertThat(d.subscriberDaily().get(4).views()).isEqualTo(2);
    assertThat(d.subscriberDaily().get(6).views()).isEqualTo(3);
  }

  @Test
  void seriesDetail_buildsReadThroughFunnel() {
    com.example.short_link.post.domain.SeriesEntity s =
        new com.example.short_link.post.domain.SeriesEntity(USER, "s1", "My Series");
    org.springframework.test.util.ReflectionTestUtils.setField(s, "id", 9L);
    PostEntity ep1 = post(USER, "ep1", 100, 5);
    PostEntity ep2 = post(USER, "ep2", 60, 3);
    PostEntity ep3 = post(USER, "ep3", 40, 2);
    org.springframework.test.util.ReflectionTestUtils.setField(ep1, "id", 1L);
    org.springframework.test.util.ReflectionTestUtils.setField(ep2, "id", 2L);
    org.springframework.test.util.ReflectionTestUtils.setField(ep3, "id", 3L);
    when(seriesRepository.findById(9L)).thenReturn(Optional.of(s));
    when(postRepository.findAllBySeriesIdOrderBySeriesOrderAsc(9L))
        .thenReturn(List.of(ep1, ep2, ep3));
    when(subscriptionRepository.countBySeriesId(9L)).thenReturn(3L);
    // Reader sets chosen so each step has a drop-off (v9 / v5 don't carry over):
    // ep1∩ep2 = {v2,v3} (2 of ep1's 4 continued), ep2∩ep3 = {v2,v3} (2 of ep2's 3 continued).
    when(viewEventRepository.readersByPostId(List.of(1L, 2L, 3L)))
        .thenReturn(
            Map.of(
                1L, java.util.Set.of("v1", "v2", "v3", "v4"),
                2L, java.util.Set.of("v2", "v3", "v9"),
                3L, java.util.Set.of("v2", "v3", "v5")));
    when(followReader.countBySourcePostIdIn(List.of(1L, 2L, 3L)))
        .thenReturn(Map.of(1L, 4L, 2L, 1L));

    SeriesAnalyticsDetail d = service.seriesDetail(USER, 9L, 7);

    assertThat(d.members()).hasSize(3);
    assertThat(d.members().get(0))
        .satisfies(
            m -> {
              assertThat(m.episode()).isEqualTo(1);
              assertThat(m.slug()).isEqualTo("ep1");
              assertThat(m.views()).isEqualTo(100);
              assertThat(m.follows()).isEqualTo(4);
              assertThat(m.uniqueReaders()).isEqualTo(4);
              assertThat(m.continuedToNext()).isEqualTo(2);
            });
    assertThat(d.members().get(1))
        .satisfies(
            m -> {
              assertThat(m.episode()).isEqualTo(2);
              assertThat(m.uniqueReaders()).isEqualTo(3);
              assertThat(m.continuedToNext()).isEqualTo(2);
              assertThat(m.follows()).isEqualTo(1);
            });
    assertThat(d.members().get(2))
        .satisfies(
            m -> {
              assertThat(m.episode()).isEqualTo(3);
              assertThat(m.uniqueReaders()).isEqualTo(3);
              assertThat(m.continuedToNext()).isZero(); // last episode — nothing follows
              assertThat(m.follows()).isZero(); // not in the follows map → default 0
            });
  }

  @Test
  void seriesDetail_rejectsOtherOwner() {
    com.example.short_link.post.domain.SeriesEntity s =
        new com.example.short_link.post.domain.SeriesEntity(OTHER, "hers", "Hers");
    org.springframework.test.util.ReflectionTestUtils.setField(s, "id", 9L);
    when(seriesRepository.findById(9L)).thenReturn(Optional.of(s));

    assertThatThrownBy(() -> service.seriesDetail(USER, 9L, 7))
        .isInstanceOfSatisfying(
            PostException.class,
            e -> assertThat(e.errorCode()).isEqualTo(PostErrorCode.SERIES_PERMISSION_DENIED));
  }

  @Test
  void seriesDetail_notFound() {
    when(seriesRepository.findById(9L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.seriesDetail(USER, 9L, 7))
        .isInstanceOfSatisfying(
            PostException.class,
            e -> assertThat(e.errorCode()).isEqualTo(PostErrorCode.SERIES_NOT_FOUND));
  }

  @Test
  void fillDaily_fillsGapsWithZero() {
    List<DailyPoint> out =
        PostAnalyticsQueryService.fillDaily(
            List.of(new DailyViewCount(LocalDate.parse("2026-06-03"), 4)),
            LocalDate.parse("2026-06-01"),
            LocalDate.parse("2026-06-04"));

    assertThat(out).hasSize(4);
    assertThat(out).extracting(DailyPoint::views).containsExactly(0L, 0L, 4L, 0L);
  }

  private static Long eqId(long id) {
    return org.mockito.ArgumentMatchers.eq(id);
  }
}
