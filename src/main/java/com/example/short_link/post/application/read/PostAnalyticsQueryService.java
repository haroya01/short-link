package com.example.short_link.post.application.read;

import com.example.short_link.post.domain.DailyViewCount;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostFollowReader;
import com.example.short_link.post.domain.repository.PostLinkClickReader;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.domain.repository.PostViewEventRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

  private static final int DEFAULT_WINDOW_DAYS = 30;
  private static final int MAX_WINDOW_DAYS = 365;
  private static final int LINK_BREAKDOWN_LIMIT = 20;

  private final PostRepository postRepository;
  private final PostViewEventRepository viewEventRepository;
  private final PostLinkClickReader linkClickReader;
  private final PostFollowReader followReader;
  private final Clock clock;

  // @Autowired marks the constructor Spring must use — without it the extra (test-only) Clock
  // constructor makes the bean ambiguous and Spring falls back to a no-arg ctor that doesn't exist.
  @Autowired
  public PostAnalyticsQueryService(
      PostRepository postRepository,
      PostViewEventRepository viewEventRepository,
      PostLinkClickReader linkClickReader,
      PostFollowReader followReader) {
    this(postRepository, viewEventRepository, linkClickReader, followReader, Clock.systemUTC());
  }

  PostAnalyticsQueryService(
      PostRepository postRepository,
      PostViewEventRepository viewEventRepository,
      PostLinkClickReader linkClickReader,
      PostFollowReader followReader,
      Clock clock) {
    this.postRepository = postRepository;
    this.viewEventRepository = viewEventRepository;
    this.linkClickReader = linkClickReader;
    this.followReader = followReader;
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
    int window = clampWindow(days);
    LocalDate today = LocalDate.now(clock);
    LocalDate from = today.minusDays(window - 1L);
    List<DailyPoint> daily =
        fillDaily(
            viewEventRepository.countDailyByPostIdSince(postId, startOfDay(from)), from, today);
    long windowViews = daily.stream().mapToLong(DailyPoint::views).sum();
    Instant since = startOfDay(from);
    return new PostAnalyticsView(
        post.getId(),
        post.getSlug(),
        post.getTitle(),
        post.getStatus().name(),
        post.getViewCount(),
        post.getLikeCount(),
        window,
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
    int window = clampWindow(days);
    LocalDate today = LocalDate.now(clock);
    LocalDate from = today.minusDays(window - 1L);
    List<DailyPoint> daily =
        fillDaily(
            viewEventRepository.countDailyByUserIdSince(userId, startOfDay(from)), from, today);
    long windowViews = daily.stream().mapToLong(DailyPoint::views).sum();
    long lifetimeViews = posts.stream().mapToLong(PostEntity::getViewCount).sum();
    long lifetimeLikes = posts.stream().mapToLong(PostEntity::getLikeCount).sum();
    long published = posts.stream().filter(PostEntity::isPublished).count();
    // Every post gets its own row (per-post breakdown), with the follows it drove pulled in one
    // grouped query instead of N — sorted by lifetime views so the strongest posts lead.
    Map<Long, Long> followsByPost =
        followReader.countBySourcePostIdIn(posts.stream().map(PostEntity::getId).toList());
    List<TopPostView> top =
        posts.stream()
            .sorted(Comparator.comparingLong(PostEntity::getViewCount).reversed())
            .map(
                p ->
                    new TopPostView(
                        p.getId(),
                        p.getSlug(),
                        p.getTitle(),
                        p.getViewCount(),
                        p.getLikeCount(),
                        followsByPost.getOrDefault(p.getId(), 0L)))
            .toList();
    return new AuthorAnalyticsOverview(
        posts.size(),
        published,
        lifetimeViews,
        lifetimeLikes,
        window,
        windowViews,
        linkClickReader.countByUserId(userId),
        linkClickReader.countByUserIdSince(userId, startOfDay(from)),
        followReader.countByUserId(userId),
        followReader.countByUserIdSince(userId, startOfDay(from)),
        daily,
        top);
  }

  private int clampWindow(int days) {
    if (days <= 0) {
      return DEFAULT_WINDOW_DAYS;
    }
    return Math.min(days, MAX_WINDOW_DAYS);
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
