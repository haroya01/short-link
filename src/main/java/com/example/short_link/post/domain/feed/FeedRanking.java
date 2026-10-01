package com.example.short_link.post.domain.feed;

import java.time.Duration;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public final class FeedRanking {

  public static final int CANDIDATE_POOL_SIZE = 500;

  public static final Duration TRENDING_WINDOW = Duration.ofDays(7);

  private static final Comparator<FeedCandidate> NEWEST_FIRST =
      Comparator.comparing(FeedCandidate::publishedAt)
          .thenComparingLong(FeedCandidate::postId)
          .reversed();

  private FeedRanking() {}

  public static List<FeedCandidate> recent(Collection<FeedCandidate> pool) {
    return newestPerSeries(pool).sorted(NEWEST_FIRST).toList();
  }

  public static List<FeedCandidate> trending(
      Collection<FeedCandidate> pool, Map<Long, Long> recentViews) {
    Comparator<FeedCandidate> mostViewed =
        Comparator.comparingLong((FeedCandidate c) -> recentViews.getOrDefault(c.postId(), 0L))
            .reversed();
    return newestPerSeries(pool).sorted(mostViewed.thenComparing(NEWEST_FIRST)).toList();
  }

  public static List<FeedCandidate> forYou(
      Collection<FeedCandidate> pool,
      long viewerId,
      Collection<String> interestTags,
      Collection<Long> excludedPostIds) {
    Set<String> interest = Set.copyOf(interestTags);
    Set<Long> excluded = Set.copyOf(excludedPostIds);
    return pool.stream()
        .filter(c -> c.authorId() != viewerId)
        .filter(c -> !excluded.contains(c.postId()))
        .filter(c -> c.normalizedTags().stream().anyMatch(interest::contains))
        .sorted(NEWEST_FIRST)
        .toList();
  }

  private static Stream<FeedCandidate> newestPerSeries(Collection<FeedCandidate> pool) {
    Map<Long, FeedCandidate> newest = new HashMap<>();
    for (FeedCandidate c : pool) {
      if (c.seriesId() != null) {
        newest.merge(c.seriesId(), c, (a, b) -> NEWEST_FIRST.compare(a, b) <= 0 ? a : b);
      }
    }
    return pool.stream()
        .filter(c -> c.seriesId() == null || newest.get(c.seriesId()).postId() == c.postId());
  }
}
