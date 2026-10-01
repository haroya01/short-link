package com.example.short_link.post.domain.feed.replay;

import com.example.short_link.post.domain.feed.FeedCandidate;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// For You as it ran before v1 (#710), frozen here with its own constants so the baseline stays put
// while the production core moves on.
final class ForYouV0 {

  private static final int POOL = 500;
  private static final int EXCLUDED_READS = 200;
  private static final int SIGNAL_POSTS = 40;
  private static final int FOLLOWED_WEIGHT = 3;
  private static final int MAX_TAGS = 12;

  private static final Comparator<FeedCandidate> NEWEST_FIRST =
      Comparator.comparing(FeedCandidate::publishedAt)
          .thenComparingLong(FeedCandidate::postId)
          .reversed();

  private ForYouV0() {}

  static List<Long> signalPostIds(List<Long> readsNewestFirst, List<Long> likesNewestFirst) {
    return Stream.concat(
            readsNewestFirst.stream().limit(SIGNAL_POSTS),
            likesNewestFirst.stream().limit(SIGNAL_POSTS))
        .distinct()
        .toList();
  }

  static List<String> topTags(
      Collection<String> followedTags,
      Collection<? extends Collection<String>> signalPostTags,
      Collection<String> hiddenTags) {
    Map<String, Integer> weight = new HashMap<>();
    followedTags.forEach(
        t -> weight.merge(t.toLowerCase(Locale.ROOT), FOLLOWED_WEIGHT, Integer::sum));
    signalPostTags.forEach(
        tags -> tags.forEach(t -> weight.merge(t.toLowerCase(Locale.ROOT), 1, Integer::sum)));
    hiddenTags.forEach(t -> weight.remove(t.toLowerCase(Locale.ROOT)));
    return weight.entrySet().stream()
        .sorted(
            Map.Entry.<String, Integer>comparingByValue()
                .reversed()
                .thenComparing(Map.Entry.comparingByKey()))
        .limit(MAX_TAGS)
        .map(Map.Entry::getKey)
        .toList();
  }

  static List<FeedCandidate> rank(
      Collection<FeedCandidate> pool,
      long viewerId,
      Collection<String> interestTags,
      List<Long> readsNewestFirst) {
    Set<String> interest = Set.copyOf(interestTags);
    Set<Long> excluded =
        readsNewestFirst.stream().limit(EXCLUDED_READS).collect(Collectors.toSet());
    return pool.stream()
        .sorted(NEWEST_FIRST)
        .limit(POOL)
        .filter(c -> c.authorId() != viewerId)
        .filter(c -> !excluded.contains(c.postId()))
        .filter(c -> c.normalizedTags().stream().anyMatch(interest::contains))
        .toList();
  }
}
