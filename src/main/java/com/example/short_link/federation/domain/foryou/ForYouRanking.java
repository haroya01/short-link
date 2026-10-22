package com.example.short_link.federation.domain.foryou;

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
import java.util.Set;

// StrictMath keeps scores bit-identical across JVMs; the replay's score contract depends on it.
public final class ForYouRanking {

  public static final int MAX_RANKED = 400;

  public record Weights(
      double reaction,
      double language,
      double interest,
      Duration freshness,
      int block,
      int authorSlots,
      int hostSlots) {

    public static final Weights DEFAULT =
        new Weights(0.6, 3.0, 3.0, Duration.ofHours(48), 20, 2, 10);
  }

  public record Ranked(List<ForYouCandidate> items, int seenFrom) {

    public Ranked {
      items = List.copyOf(items);
    }
  }

  private record Scored(ForYouCandidate candidate, double score) {}

  static final Comparator<Scored> BEST_FIRST =
      Comparator.comparingDouble(Scored::score)
          .reversed()
          .thenComparing(scored -> scored.candidate().order(), Comparator.reverseOrder())
          .thenComparing(scored -> scored.candidate().uri());

  private ForYouRanking() {}

  public static Ranked rank(
      Collection<ForYouCandidate> pool, ForYouReader reader, Set<String> seen, Instant now) {
    return rank(pool, reader, seen, now, Weights.DEFAULT);
  }

  public static Ranked rank(
      Collection<ForYouCandidate> pool,
      ForYouReader reader,
      Set<String> seen,
      Instant now,
      Weights weights) {
    Map<String, Double> idf = inverseDocumentFrequency(pool);
    Map<String, Double> interest = new HashMap<>();
    reader
        .tags()
        .forEach(
            (tag, weight) -> {
              Double tagIdf = idf.get(tag);
              if (tagIdf != null) {
                interest.put(tag, weight * tagIdf);
              }
            });
    double interestNorm = norm(interest.values());
    List<Scored> unseen = new ArrayList<>();
    List<Scored> already = new ArrayList<>();
    for (ForYouCandidate candidate : pool) {
      Scored scored =
          new Scored(
              candidate, score(candidate, reader, interest, interestNorm, idf, now, weights));
      (seen.contains(candidate.uri()) ? already : unseen).add(scored);
    }
    unseen.sort(BEST_FIRST);
    already.sort(BEST_FIRST);
    List<ForYouCandidate> items = new ArrayList<>(mix(unseen, weights));
    int seenFrom = -1;
    if (!already.isEmpty() && items.size() < MAX_RANKED) {
      seenFrom = items.size();
      items.addAll(mix(already, weights));
    }
    return new Ranked(items.size() > MAX_RANKED ? items.subList(0, MAX_RANKED) : items, seenFrom);
  }

  private static double score(
      ForYouCandidate candidate,
      ForYouReader reader,
      Map<String, Double> interest,
      double interestNorm,
      Map<String, Double> idf,
      Instant now,
      Weights weights) {
    double topic = cosine(candidate, interest, interestNorm, idf);
    double author = Math.min(1.0, reader.authors().getOrDefault(candidate.author(), 0.0) / 3.0);
    double liking = 1 - (1 - topic) * (1 - author);
    return weights.reaction() * StrictMath.log1p(candidate.engagement())
        + weights.language() * language(candidate, reader)
        + weights.interest() * liking
        - age(candidate, now, weights.freshness());
  }

  static double language(ForYouCandidate candidate, ForYouReader reader) {
    if (reader.languages().isEmpty()) {
      return 0;
    }
    if (candidate.language() == null) {
      return 0.5;
    }
    return reader.languages().contains(candidate.language()) ? 1 : 0;
  }

  private static List<ForYouCandidate> mix(List<Scored> ranked, Weights weights) {
    LinkedList<ForYouCandidate> remaining = new LinkedList<>();
    ranked.forEach(scored -> remaining.add(scored.candidate()));
    List<ForYouCandidate> mixed = new ArrayList<>(ranked.size());
    while (!remaining.isEmpty() && mixed.size() < MAX_RANKED) {
      Map<String, Integer> authors = new HashMap<>();
      Map<String, Integer> hosts = new HashMap<>();
      int blockEnd = Math.min(mixed.size() + weights.block(), mixed.size() + remaining.size());
      while (mixed.size() < blockEnd) {
        ForYouCandidate pick = take(remaining, authors, hosts, weights, true);
        if (pick == null) {
          pick = take(remaining, authors, hosts, weights, false);
        }
        if (pick == null) {
          pick = remaining.removeFirst();
        }
        mixed.add(pick);
        authors.merge(pick.author(), 1, Integer::sum);
        hosts.merge(pick.host(), 1, Integer::sum);
      }
    }
    return mixed;
  }

  private static ForYouCandidate take(
      LinkedList<ForYouCandidate> remaining,
      Map<String, Integer> authors,
      Map<String, Integer> hosts,
      Weights weights,
      boolean withHosts) {
    for (Iterator<ForYouCandidate> it = remaining.iterator(); it.hasNext(); ) {
      ForYouCandidate candidate = it.next();
      if (authors.getOrDefault(candidate.author(), 0) < weights.authorSlots()
          && (!withHosts || hosts.getOrDefault(candidate.host(), 0) < weights.hostSlots())) {
        it.remove();
        return candidate;
      }
    }
    return null;
  }

  private static Map<String, Double> inverseDocumentFrequency(Collection<ForYouCandidate> pool) {
    Map<String, Integer> withTag = new HashMap<>();
    pool.forEach(candidate -> candidate.tags().forEach(tag -> withTag.merge(tag, 1, Integer::sum)));
    Map<String, Double> idf = new HashMap<>();
    withTag.forEach((tag, count) -> idf.put(tag, StrictMath.log1p((double) pool.size() / count)));
    return idf;
  }

  private static double cosine(
      ForYouCandidate candidate,
      Map<String, Double> interest,
      double interestNorm,
      Map<String, Double> idf) {
    if (interestNorm == 0 || candidate.tags().isEmpty()) {
      return 0;
    }
    double dot = 0;
    double squares = 0;
    for (String tag : candidate.tags()) {
      double tagIdf = idf.getOrDefault(tag, 0.0);
      dot += interest.getOrDefault(tag, 0.0) * tagIdf;
      squares += tagIdf * tagIdf;
    }
    return squares == 0 ? 0 : dot / (interestNorm * StrictMath.sqrt(squares));
  }

  private static double age(ForYouCandidate candidate, Instant now, Duration freshness) {
    long seconds = Math.max(0, Duration.between(candidate.createdAt(), now).toSeconds());
    return (double) seconds / freshness.toSeconds();
  }

  private static double norm(Collection<Double> values) {
    double squares = 0;
    for (double value : values) {
      squares += value * value;
    }
    return StrictMath.sqrt(squares);
  }
}
