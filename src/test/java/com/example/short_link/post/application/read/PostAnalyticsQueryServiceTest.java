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

  private PostAnalyticsQueryService service;

  @BeforeEach
  void setUp() {
    service = new PostAnalyticsQueryService(postRepository, viewEventRepository, clock);
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
  }

  @Test
  void postAnalytics_defaultsWindowWhenNonPositive() {
    PostEntity p = post(USER, "hello", 0, 0);
    when(postRepository.findById(1L)).thenReturn(Optional.of(p));
    when(viewEventRepository.countDailyByPostIdSince(eqId(1L), org.mockito.ArgumentMatchers.any()))
        .thenReturn(List.of());

    PostAnalyticsView view = service.postAnalytics(USER, 1L, 0);

    assertThat(view.windowDays()).isEqualTo(30);
    assertThat(view.daily()).hasSize(30);
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
  void overview_aggregatesTotalsAndRanksTopPosts() {
    PostEntity a = post(USER, "a", 100, 10);
    a.publish();
    PostEntity b = post(USER, "b", 5, 1); // draft
    PostEntity c = post(USER, "c", 50, 3);
    c.publish();
    when(postRepository.findAllByUserIdOrderByCreatedAtDesc(USER)).thenReturn(List.of(a, b, c));
    when(viewEventRepository.countDailyByUserIdSince(
            org.mockito.ArgumentMatchers.eq(USER), org.mockito.ArgumentMatchers.any()))
        .thenReturn(List.of(new DailyViewCount(LocalDate.parse("2026-06-01"), 9)));

    AuthorAnalyticsOverview o = service.overview(USER, 30);

    assertThat(o.totalPosts()).isEqualTo(3);
    assertThat(o.publishedPosts()).isEqualTo(2);
    assertThat(o.lifetimeViews()).isEqualTo(155);
    assertThat(o.lifetimeLikes()).isEqualTo(14);
    assertThat(o.windowViews()).isEqualTo(9);
    assertThat(o.daily()).hasSize(30);
    // Top posts ranked by lifetime views desc: a(100), c(50), b(5).
    assertThat(o.topPosts()).extracting(TopPostView::slug).containsExactly("a", "c", "b");
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
