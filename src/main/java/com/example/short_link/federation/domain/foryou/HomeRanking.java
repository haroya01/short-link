package com.example.short_link.federation.domain.foryou;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// StrictMath keeps scores bit-identical across JVMs; the home-rank vectors compare them.
public final class HomeRanking {

  public record Weights(
      double reaction,
      double freshness,
      double freshnessOffsetHours,
      double language,
      double interest,
      double follow,
      double local,
      int block,
      int authorSlots,
      int hostSlots,
      int maxRanked,
      Duration minePinned,
      Duration localWindow,
      Duration remoteWindow,
      int remotePool) {

    public static final Weights DEFAULT =
        new Weights(
            1.0,
            1.5,
            2.0,
            3.0,
            3.0,
            2.0,
            1.0,
            20,
            2,
            10,
            400,
            Duration.ofMinutes(60),
            Duration.ofDays(7),
            Duration.ofHours(72),
            1_000);
  }

  public record Reader(ForYouReader taste, Long member, Set<String> follows) {

    public static final Reader NONE = new Reader(ForYouReader.NONE, null, Set.of());

    public Reader {
      follows = member == null ? Set.of() : Set.copyOf(follows);
    }
  }

  public record Ranked(List<HomeCandidate> items, int pinned, Map<String, Double> scores) {

    public Ranked {
      items = List.copyOf(items);
      scores = Map.copyOf(scores);
    }
  }

  private record Scored(HomeCandidate candidate, double score) {}

  private static final Comparator<Scored> BEST_FIRST =
      Comparator.comparingDouble(Scored::score)
          .reversed()
          .thenComparing(scored -> scored.candidate().item().createdAt(), Comparator.reverseOrder())
          .thenComparing(scored -> scored.candidate().key());

  private static final Comparator<HomeCandidate> NEWEST_FIRST =
      Comparator.comparing((HomeCandidate mine) -> mine.item().createdAt())
          .reversed()
          .thenComparing(HomeCandidate::key);

  private HomeRanking() {}

  public static Ranked rank(
      Collection<HomeCandidate> candidates,
      Reader reader,
      Set<String> seen,
      boolean hideSeen,
      Instant asOf) {
    return rank(candidates, reader, seen, hideSeen, asOf, Weights.DEFAULT);
  }

  public static Ranked rank(
      Collection<HomeCandidate> candidates,
      Reader reader,
      Set<String> seen,
      boolean hideSeen,
      Instant asOf,
      Weights weights) {
    List<HomeCandidate> pinned = new ArrayList<>();
    List<HomeCandidate> pool = new ArrayList<>();
    for (HomeCandidate candidate : pool(candidates, asOf, weights)) {
      if (reader.member() != null
          && candidate.local()
          && reader.member().equals(candidate.owner())) {
        boolean fresh = !candidate.item().createdAt().isBefore(asOf.minus(weights.minePinned()));
        if (fresh && !(hideSeen && seen.contains(candidate.key()))) {
          pinned.add(candidate);
        }
        continue;
      }
      pool.add(candidate);
    }
    pinned.sort(NEWEST_FIRST);
    ForYouRanking.Taste taste =
        ForYouRanking.Taste.of(pool.stream().map(HomeCandidate::item).toList(), reader.taste());
    Map<String, Double> scores = new HashMap<>();
    List<Scored> shown = new ArrayList<>();
    for (HomeCandidate candidate : pool) {
      double score = score(candidate, reader, taste, asOf, weights);
      scores.put(candidate.key(), score);
      if (!(hideSeen && seen.contains(candidate.key()))) {
        shown.add(new Scored(candidate, score));
      }
    }
    shown.sort(BEST_FIRST);
    Map<ForYouCandidate, HomeCandidate> byItem = new IdentityHashMap<>();
    shown.forEach(scored -> byItem.put(scored.candidate().item(), scored.candidate()));
    List<HomeCandidate> items = new ArrayList<>(pinned);
    ForYouRanking.mix(
            shown.stream().map(scored -> scored.candidate().item()).toList(),
            weights.block(),
            weights.authorSlots(),
            weights.hostSlots(),
            weights.maxRanked())
        .stream()
        .limit(weights.maxRanked())
        .forEach(item -> items.add(byItem.get(item)));
    return new Ranked(items, pinned.size(), scores);
  }

  private static List<HomeCandidate> pool(
      Collection<HomeCandidate> candidates, Instant asOf, Weights weights) {
    Instant localSince = asOf.minus(weights.localWindow());
    Instant remoteSince = asOf.minus(weights.remoteWindow());
    List<HomeCandidate> within = new ArrayList<>();
    List<HomeCandidate> remotes = new ArrayList<>();
    for (HomeCandidate candidate : candidates) {
      Instant created = candidate.item().createdAt();
      if (candidate.local()) {
        if (!created.isBefore(localSince) && !created.isAfter(asOf)) {
          within.add(candidate);
        }
      } else if (!candidate.received().isBefore(remoteSince)
          && !candidate.received().isAfter(asOf)
          && !created.isBefore(remoteSince)) {
        within.add(candidate);
        remotes.add(candidate);
      }
    }
    Set<HomeCandidate> kept = Collections.newSetFromMap(new IdentityHashMap<>());
    remotes.stream()
        .sorted(Comparator.comparingLong((HomeCandidate r) -> r.item().order()).reversed())
        .limit(weights.remotePool())
        .forEach(kept::add);
    Set<Long> localNotes = new HashSet<>();
    within.stream()
        .filter(candidate -> candidate.source() == HomeCandidate.Source.NOTE)
        .forEach(candidate -> localNotes.add(candidate.noteId()));
    Set<String> keys = new HashSet<>();
    Set<Long> remoteNotes = new HashSet<>();
    List<HomeCandidate> pool = new ArrayList<>(within.size());
    for (HomeCandidate candidate : within) {
      boolean remote = !candidate.local();
      if (remote && (!kept.contains(candidate) || localNotes.contains(candidate.noteId()))) {
        continue;
      }
      if (keys.contains(candidate.key())
          || (remote && candidate.noteId() != null && remoteNotes.contains(candidate.noteId()))) {
        continue;
      }
      keys.add(candidate.key());
      if (remote) {
        remoteNotes.add(candidate.noteId());
      }
      pool.add(candidate);
    }
    return pool;
  }

  private static double score(
      HomeCandidate candidate,
      Reader reader,
      ForYouRanking.Taste taste,
      Instant asOf,
      Weights weights) {
    ForYouCandidate item = candidate.item();
    long seconds = Math.max(0, Duration.between(item.createdAt(), asOf).toSeconds());
    double hours = (double) seconds / 3_600;
    return weights.reaction() * StrictMath.log1p(item.engagement())
        - weights.freshness() * StrictMath.log(weights.freshnessOffsetHours() + hours)
        + weights.language() * ForYouRanking.language(item, reader.taste())
        + weights.interest() * taste.liking(item, reader.taste())
        + weights.follow() * (reader.follows().contains(item.author()) ? 1 : 0)
        + weights.local() * (candidate.local() ? 1 : 0);
  }
}
