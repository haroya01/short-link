package com.example.short_link.post.domain.feed;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

// StrictMath keeps scores bit-identical across JVMs; the offline replay snapshot depends on it.
public final class ForYouRanking {

  public static final Duration FRESHNESS_SCALE = Duration.ofDays(21);

  public static final int AUTHOR_BLOCK = 10;

  public static final int AUTHOR_SLOTS = 2;

  public static final int FAMILIAR_AUTHOR_READS = 3;

  public record Weights(
      double topic,
      double language,
      double freshness,
      double popularity,
      boolean discountCommonTags,
      boolean mixAuthors) {

    public static final Weights DEFAULT = new Weights(3.0, 1.5, 1.0, 0.3, true, true);
  }

  public record Reader(
      long id,
      Map<String, Integer> interest,
      Set<String> hiddenTags,
      Set<String> languages,
      List<Long> readsNewestFirst,
      Set<Long> familiarAuthors) {

    public static Reader of(
        long id,
        String locale,
        Collection<String> followedTags,
        Collection<String> hiddenTags,
        List<Long> readsNewestFirst,
        List<Long> likesNewestFirst,
        Map<Long, FeedCandidate> signalPosts) {
      List<List<String>> signalTags =
          InterestProfile.signalPostIds(readsNewestFirst, likesNewestFirst).stream()
              .map(signalPosts::get)
              .filter(Objects::nonNull)
              .map(FeedCandidate::tags)
              .toList();
      List<FeedCandidate> recentReads =
          readsNewestFirst.stream()
              .limit(InterestProfile.SIGNAL_POSTS)
              .map(signalPosts::get)
              .filter(Objects::nonNull)
              .toList();
      Map<Long, Long> readsByAuthor =
          recentReads.stream()
              .collect(Collectors.groupingBy(FeedCandidate::authorId, Collectors.counting()));
      return new Reader(
          id,
          InterestProfile.weights(followedTags, signalTags, hiddenTags),
          hiddenTags.stream().map(InterestProfile::normalize).collect(Collectors.toSet()),
          InterestProfile.languages(
              locale, recentReads.stream().map(FeedCandidate::languageTag).toList()),
          readsNewestFirst,
          readsByAuthor.entrySet().stream()
              .filter(e -> e.getValue() >= FAMILIAR_AUTHOR_READS)
              .map(Map.Entry::getKey)
              .collect(Collectors.toSet()));
    }
  }

  private record Scored(FeedCandidate candidate, double score) {}

  private ForYouRanking() {}

  public static List<FeedCandidate> rank(
      Collection<FeedCandidate> pool,
      Reader reader,
      Map<Long, Long> recentHumanViews,
      Instant now) {
    return rank(pool, reader, recentHumanViews, now, Weights.DEFAULT);
  }

  public static List<FeedCandidate> rank(
      Collection<FeedCandidate> pool,
      Reader reader,
      Map<Long, Long> recentHumanViews,
      Instant now,
      Weights weights) {
    List<FeedCandidate> candidates =
        pool.stream()
            .sorted(FeedRanking.NEWEST_FIRST)
            .limit(FeedRanking.CANDIDATE_POOL_SIZE)
            .toList();
    Map<String, Double> idf = inverseDocumentFrequency(candidates, weights.discountCommonTags());
    Map<String, Double> interest = new HashMap<>();
    reader
        .interest()
        .forEach(
            (tag, weight) -> {
              Double tagIdf = idf.get(tag);
              if (tagIdf != null) interest.put(tag, weight * tagIdf);
            });
    double interestNorm = norm(interest.values());
    Set<Long> excluded =
        reader.readsNewestFirst().stream()
            .limit(FeedRanking.EXCLUDED_READS)
            .collect(Collectors.toSet());

    List<FeedCandidate> ranked =
        candidates.stream()
            .filter(c -> c.authorId() != reader.id())
            .filter(c -> !excluded.contains(c.postId()))
            .filter(c -> c.normalizedTags().stream().noneMatch(reader.hiddenTags()::contains))
            .map(
                c ->
                    new Scored(
                        c,
                        weights.topic() * cosine(c, interest, interestNorm, idf)
                            + weights.language()
                                * (reader.languages().contains(c.languageTag()) ? 1 : 0)
                            - weights.freshness() * age(c, now)
                            + weights.popularity()
                                * StrictMath.log1p(recentHumanViews.getOrDefault(c.postId(), 0L))))
            .sorted(
                Comparator.comparingDouble(Scored::score)
                    .reversed()
                    .thenComparing(Scored::candidate, FeedRanking.NEWEST_FIRST))
            .map(Scored::candidate)
            .toList();
    Map<Boolean, List<FeedCandidate>> sharesInterest =
        ranked.stream()
            .collect(
                Collectors.partitioningBy(
                    c -> c.normalizedTags().stream().anyMatch(reader.interest()::containsKey)));
    List<FeedCandidate> feed =
        new ArrayList<>(mix(sharesInterest.get(true), reader.familiarAuthors(), weights));
    feed.addAll(mix(sharesInterest.get(false), reader.familiarAuthors(), weights));
    return feed;
  }

  private static List<FeedCandidate> mix(
      List<FeedCandidate> ranked, Set<Long> familiarAuthors, Weights weights) {
    return weights.mixAuthors() ? mixAuthors(ranked, familiarAuthors) : ranked;
  }

  static List<FeedCandidate> mixAuthors(List<FeedCandidate> ranked, Set<Long> familiarAuthors) {
    LinkedList<FeedCandidate> remaining = new LinkedList<>(ranked);
    List<FeedCandidate> mixed = new ArrayList<>(ranked.size());
    while (!remaining.isEmpty()) {
      Map<Long, Integer> inBlock = new HashMap<>();
      int blockEnd = Math.min(mixed.size() + AUTHOR_BLOCK, ranked.size());
      while (mixed.size() < blockEnd) {
        FeedCandidate pick = null;
        for (Iterator<FeedCandidate> it = remaining.iterator(); it.hasNext(); ) {
          FeedCandidate c = it.next();
          if (familiarAuthors.contains(c.authorId())
              || inBlock.getOrDefault(c.authorId(), 0) < AUTHOR_SLOTS) {
            pick = c;
            it.remove();
            break;
          }
        }
        if (pick == null) {
          pick = remaining.removeFirst();
        }
        mixed.add(pick);
        inBlock.merge(pick.authorId(), 1, Integer::sum);
      }
    }
    return mixed;
  }

  private static Map<String, Double> inverseDocumentFrequency(
      List<FeedCandidate> candidates, boolean discountCommonTags) {
    Map<String, Integer> postsWithTag = new HashMap<>();
    candidates.forEach(
        c -> c.normalizedTags().forEach(t -> postsWithTag.merge(t, 1, Integer::sum)));
    Map<String, Double> idf = new HashMap<>();
    postsWithTag.forEach(
        (tag, count) ->
            idf.put(
                tag,
                discountCommonTags ? StrictMath.log1p((double) candidates.size() / count) : 1.0));
    return idf;
  }

  private static double cosine(
      FeedCandidate c, Map<String, Double> interest, double interestNorm, Map<String, Double> idf) {
    if (interestNorm == 0) {
      return 0;
    }
    double dot = 0;
    double postSquares = 0;
    for (String tag : c.normalizedTags()) {
      double tagIdf = idf.get(tag);
      dot += interest.getOrDefault(tag, 0.0) * tagIdf;
      postSquares += tagIdf * tagIdf;
    }
    return postSquares == 0 ? 0 : dot / (interestNorm * StrictMath.sqrt(postSquares));
  }

  private static double age(FeedCandidate c, Instant now) {
    long seconds = Math.max(0, Duration.between(c.publishedAt(), now).toSeconds());
    return (double) seconds / FRESHNESS_SCALE.toSeconds();
  }

  private static double norm(Collection<Double> values) {
    double squares = 0;
    for (double v : values) {
      squares += v * v;
    }
    return StrictMath.sqrt(squares);
  }
}
