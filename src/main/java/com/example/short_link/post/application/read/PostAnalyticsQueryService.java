package com.example.short_link.post.application.read;

import com.example.short_link.post.domain.DailyViewCount;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.SeriesEntity;
import com.example.short_link.post.domain.repository.PostFollowReader;
import com.example.short_link.post.domain.repository.PostLinkClickReader;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.domain.repository.PostViewEventRepository;
import com.example.short_link.post.domain.repository.SeriesRepository;
import com.example.short_link.post.domain.repository.SeriesSubscriptionRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read side of author analytics. Lifetime totals come from the denormalized counters on the post
 * (views/likes shown on cards); the view-over-time line comes from the post_view_event log, grouped
 * per UTC day and then gap-filled here so the dashboard draws a continuous series. Ownership is
 * enforced for the per-post view — analytics are private to the author.
 */
@Service
@Transactional(readOnly = true)
public class PostAnalyticsQueryService {

  private static final int MAX_WINDOW_DAYS = 365;
  private static final int LINK_BREAKDOWN_LIMIT = 20;
  private static final int DEFAULT_PAGE_SIZE = 20;
  private static final int MAX_PAGE_SIZE = 50;

  private final PostRepository postRepository;
  private final PostViewEventRepository viewEventRepository;
  private final PostLinkClickReader linkClickReader;
  private final PostFollowReader followReader;
  private final SeriesRepository seriesRepository;
  private final SeriesSubscriptionRepository subscriptionRepository;
  private final Clock clock;

  // @Autowired marks the constructor Spring must use — without it the extra (test-only) Clock
  // constructor makes the bean ambiguous and Spring falls back to a no-arg ctor that doesn't exist.
  @Autowired
  public PostAnalyticsQueryService(
      PostRepository postRepository,
      PostViewEventRepository viewEventRepository,
      PostLinkClickReader linkClickReader,
      PostFollowReader followReader,
      SeriesRepository seriesRepository,
      SeriesSubscriptionRepository subscriptionRepository) {
    this(
        postRepository,
        viewEventRepository,
        linkClickReader,
        followReader,
        seriesRepository,
        subscriptionRepository,
        Clock.systemUTC());
  }

  PostAnalyticsQueryService(
      PostRepository postRepository,
      PostViewEventRepository viewEventRepository,
      PostLinkClickReader linkClickReader,
      PostFollowReader followReader,
      SeriesRepository seriesRepository,
      SeriesSubscriptionRepository subscriptionRepository,
      Clock clock) {
    this.postRepository = postRepository;
    this.viewEventRepository = viewEventRepository;
    this.linkClickReader = linkClickReader;
    this.followReader = followReader;
    this.seriesRepository = seriesRepository;
    this.subscriptionRepository = subscriptionRepository;
    this.clock = clock;
  }

  public PostAnalyticsView postAnalytics(Long userId, Long postId, int days) {
    PostEntity post =
        postRepository
            .findById(postId)
            .orElseThrow(() -> new PostException(PostErrorCode.POST_NOT_FOUND, postId));
    if (!post.isOwnedBy(userId)) {
      throw new PostException(PostErrorCode.PERMISSION_DENIED).with("postId", postId);
    }
    LocalDate today = LocalDate.now(clock);
    Instant since = fetchSince(days, today);
    List<DailyViewCount> sparse = viewEventRepository.countDailyByPostIdSince(postId, since);
    Window w = resolveWindow(days, sparse, today);
    List<DailyPoint> daily = fillDaily(sparse, w.from(), today);
    long windowViews = daily.stream().mapToLong(DailyPoint::views).sum();
    return new PostAnalyticsView(
        post.getId(),
        post.getSlug(),
        post.getTitle(),
        post.getStatus().name(),
        post.getViewCount(),
        post.getLikeCount(),
        w.windowDays(),
        windowViews,
        linkClickReader.countByPostId(postId),
        linkClickReader.countByPostIdSince(postId, since),
        followReader.countBySourcePostId(postId),
        followReader.countBySourcePostIdSince(postId, since),
        daily,
        linkClickReader.breakdownByPostId(postId, LINK_BREAKDOWN_LIMIT));
  }

  public AuthorAnalyticsOverview overview(Long userId, int days) {
    List<PostEntity> posts = postRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
    LocalDate today = LocalDate.now(clock);
    Instant since = fetchSince(days, today);
    List<DailyViewCount> sparse = viewEventRepository.countDailyByUserIdSince(userId, since);
    Window w = resolveWindow(days, sparse, today);
    List<DailyPoint> daily = fillDaily(sparse, w.from(), today);
    long windowViews = daily.stream().mapToLong(DailyPoint::views).sum();
    long lifetimeViews = posts.stream().mapToLong(PostEntity::getViewCount).sum();
    long lifetimeLikes = posts.stream().mapToLong(PostEntity::getLikeCount).sum();
    long published = posts.stream().filter(PostEntity::isPublished).count();
    return new AuthorAnalyticsOverview(
        posts.size(),
        published,
        lifetimeViews,
        lifetimeLikes,
        w.windowDays(),
        windowViews,
        linkClickReader.countByUserId(userId),
        linkClickReader.countByUserIdSince(userId, since),
        followReader.countByUserId(userId),
        followReader.countByUserIdSince(userId, since),
        daily);
  }

  /**
   * One page of the author's per-post performance (views·likes·follows), ordered by {@code sort}.
   * Only posts that have been public (PUBLISHED / UNPUBLISHED) appear — drafts have no reads. The
   * follows each post drove are pulled for just this page in a single grouped query.
   */
  public PostPerformanceResult postPerformance(
      Long userId,
      int page,
      int size,
      com.example.short_link.post.domain.PostPerformanceSort sort) {
    int p = Math.max(0, page);
    int sz = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
    List<PostEntity> posts = postRepository.findUserAnalyticsPosts(userId, p, sz, sort);
    Map<Long, Long> followsByPost =
        followReader.countBySourcePostIdIn(posts.stream().map(PostEntity::getId).toList());
    List<TopPostView> items =
        posts.stream()
            .map(
                post ->
                    new TopPostView(
                        post.getId(),
                        post.getSlug(),
                        post.getTitle(),
                        post.getViewCount(),
                        post.getLikeCount(),
                        followsByPost.getOrDefault(post.getId(), 0L)))
            .toList();
    boolean hasNext = (long) (p + 1) * sz < postRepository.countUserAnalyticsPosts(userId);
    return new PostPerformanceResult(items, p, hasNext);
  }

  /**
   * Per-series analytics for the author: subscriber count (the recurring-readership signal) plus
   * the traction of each series' member posts. Newest series first. Bounded by the author's series
   * count.
   */
  public List<SeriesAnalyticsRow> seriesAnalytics(Long userId) {
    List<SeriesEntity> series = seriesRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
    if (series.isEmpty()) {
      return List.of();
    }
    List<Long> seriesIds = series.stream().map(SeriesEntity::getId).toList();
    // 시리즈마다 멤버글·구독자수를 따로 조회하던 1+2N 을, 멤버글 배치 조회 + 구독자수 묶음 조회 후 메모리 집계로 축약.
    Map<Long, List<PostEntity>> membersBySeries =
        postRepository.findAllBySeriesIdInOrderBySeriesOrderAsc(seriesIds).stream()
            .collect(Collectors.groupingBy(PostEntity::getSeriesId));
    Map<Long, Long> subscribersBySeries = subscriptionRepository.countBySeriesIdIn(seriesIds);
    return series.stream()
        .map(
            s -> {
              List<PostEntity> members = membersBySeries.getOrDefault(s.getId(), List.of());
              long totalViews = members.stream().mapToLong(PostEntity::getViewCount).sum();
              long totalLikes = members.stream().mapToLong(PostEntity::getLikeCount).sum();
              return new SeriesAnalyticsRow(
                  s.getId(),
                  s.getSlug(),
                  s.getTitle(),
                  members.size(),
                  subscribersBySeries.getOrDefault(s.getId(), 0L),
                  totalViews,
                  totalLikes);
            })
        .toList();
  }

  /** One series' detail: headline row + a cumulative subscriber-growth line over the window. */
  public SeriesAnalyticsDetail seriesDetail(Long userId, Long seriesId, int days) {
    SeriesEntity series =
        seriesRepository
            .findById(seriesId)
            .orElseThrow(
                () -> new PostException(PostErrorCode.SERIES_NOT_FOUND, String.valueOf(seriesId)));
    if (!series.getUserId().equals(userId)) {
      throw new PostException(PostErrorCode.SERIES_PERMISSION_DENIED);
    }
    LocalDate today = LocalDate.now(clock);
    Instant since = fetchSince(days, today);
    List<DailyViewCount> sparse = subscriptionRepository.countDailyBySeriesIdSince(seriesId, since);
    Window w = resolveWindow(days, sparse, today);
    // New subscribers per day, accumulated into the running total the chart draws.
    List<DailyPoint> cumulative = new ArrayList<>();
    long running = 0;
    for (DailyPoint d : fillDaily(sparse, w.from(), today)) {
      running += d.views();
      cumulative.add(new DailyPoint(d.date(), running));
    }
    return new SeriesAnalyticsDetail(
        seriesRow(series), w.windowDays(), cumulative, seriesMembers(seriesId));
  }

  /**
   * Per-episode performance in series order, with the read-through funnel: each episode's distinct
   * human readers and how many of them also read the next episode. Lifetime — a reader's path
   * through the series isn't bounded by the dashboard's day-window. Bounded by the series' member
   * count (one reader-set query for the whole series).
   */
  private List<SeriesMemberStat> seriesMembers(Long seriesId) {
    List<PostEntity> members = postRepository.findAllBySeriesIdOrderBySeriesOrderAsc(seriesId);
    if (members.isEmpty()) {
      return List.of();
    }
    List<Long> ids = members.stream().map(PostEntity::getId).toList();
    Map<Long, Long> followsByPost = followReader.countBySourcePostIdIn(ids);
    Map<Long, Set<String>> readers = viewEventRepository.readersByPostId(ids);
    List<SeriesMemberStat> out = new ArrayList<>();
    for (int i = 0; i < members.size(); i++) {
      PostEntity post = members.get(i);
      Set<String> mine = readers.getOrDefault(post.getId(), Set.of());
      long continued = 0;
      if (i + 1 < members.size()) {
        Set<String> next = readers.getOrDefault(members.get(i + 1).getId(), Set.of());
        continued = intersectionSize(mine, next);
      }
      out.add(
          new SeriesMemberStat(
              post.getId(),
              post.getSlug(),
              post.getTitle(),
              i + 1,
              post.getViewCount(),
              post.getLikeCount(),
              followsByPost.getOrDefault(post.getId(), 0L),
              mine.size(),
              continued));
    }
    return out;
  }

  /** Size of {@code a ∩ b}, iterating the smaller set for the larger one's contains-checks. */
  private static long intersectionSize(Set<String> a, Set<String> b) {
    Set<String> smaller = a.size() <= b.size() ? a : b;
    Set<String> larger = smaller == a ? b : a;
    long n = 0;
    for (String v : smaller) {
      if (larger.contains(v)) {
        n++;
      }
    }
    return n;
  }

  private SeriesAnalyticsRow seriesRow(SeriesEntity series) {
    List<PostEntity> members =
        postRepository.findAllBySeriesIdOrderBySeriesOrderAsc(series.getId());
    long totalViews = members.stream().mapToLong(PostEntity::getViewCount).sum();
    long totalLikes = members.stream().mapToLong(PostEntity::getLikeCount).sum();
    return new SeriesAnalyticsRow(
        series.getId(),
        series.getSlug(),
        series.getTitle(),
        members.size(),
        subscriptionRepository.countBySeriesId(series.getId()),
        totalViews,
        totalLikes);
  }

  /**
   * Caps a positive day-window at the max; all-time (days &lt;= 0) is handled before this is
   * called.
   */
  private int clampWindow(int days) {
    return Math.min(days, MAX_WINDOW_DAYS);
  }

  /** The chart span + windowed-metric window. {@code days <= 0} means all-time (the "전체" tab). */
  private record Window(LocalDate from, int windowDays) {}

  /**
   * When-to-query-from: EPOCH for all-time (counts everything), else the start of the N-day window.
   */
  private Instant fetchSince(int days, LocalDate today) {
    return days <= 0 ? Instant.EPOCH : startOfDay(today.minusDays(clampWindow(days) - 1L));
  }

  /** All-time spans from the first day that actually has data (no leading empty stretch). */
  private Window resolveWindow(int days, List<DailyViewCount> sparse, LocalDate today) {
    if (days > 0) {
      int w = clampWindow(days);
      return new Window(today.minusDays(w - 1L), w);
    }
    LocalDate from =
        sparse.stream().map(DailyViewCount::date).min(Comparator.naturalOrder()).orElse(today);
    return new Window(from, (int) (ChronoUnit.DAYS.between(from, today) + 1));
  }

  private static Instant startOfDay(LocalDate date) {
    return date.atStartOfDay(ZoneOffset.UTC).toInstant();
  }

  /** Expands the sparse GROUP BY output into a continuous [from, to] daily line, filling 0s. */
  static List<DailyPoint> fillDaily(List<DailyViewCount> sparse, LocalDate from, LocalDate to) {
    Map<LocalDate, Long> byDate = new HashMap<>();
    for (DailyViewCount c : sparse) {
      byDate.put(c.date(), c.views());
    }
    List<DailyPoint> out = new ArrayList<>();
    for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
      out.add(new DailyPoint(d, byDate.getOrDefault(d, 0L)));
    }
    return out;
  }
}
