package com.example.short_link.federation.domain.foryou.replay;

import com.example.short_link.federation.domain.foryou.ForYouCandidate;
import com.example.short_link.federation.domain.foryou.ForYouProfile;
import com.example.short_link.federation.domain.foryou.ForYouReader;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

// Replays a world's reads in order. At each sampled read a ranker gets only what kurl could know at
// that moment (the reader's locale, likes, bookmarks, answers, long dwells, follows and what they
// saw, and the counts as they were last read) and ranks the pool; the read post's place is scored.
// Readable languages and topic taste stay hidden and only feed lang@10 and the ceiling.
final class RemoteReplay {

  static final int WARMUP_DAYS = 10;
  static final int STRIDE = 13;
  static final Duration SIGNAL_WINDOW = Duration.ofDays(30);
  static final int PER_KIND = 40;
  static final List<Duration> CHECKS =
      List.of(Duration.ZERO, Duration.ofHours(1), Duration.ofHours(6), Duration.ofHours(24));
  static final Duration TRENDING_FOR = Duration.ofHours(24);
  static final String CEILING = "ceiling";

  enum Counts {
    AT_KEEP,
    ASKED_AGAIN
  }

  record Context(
      RemoteWorld world,
      RemoteWorld.Reader reader,
      long readSeq,
      Instant now,
      List<RemoteWorld.Post> pool,
      Set<Long> seen,
      ForYouReader profile,
      Map<Counts, List<ForYouCandidate>> candidates) {}

  record Ranker(String name, Function<Context, List<Long>> rank) {}

  record Outcome(
      String ranker,
      long readSeq,
      Instant at,
      int rank,
      double lang10,
      int authors20,
      double maxHost20) {}

  private RemoteReplay() {}

  static List<Outcome> evaluate(RemoteWorld world, List<Ranker> rankers) {
    return evaluate(world, rankers, STRIDE);
  }

  static List<Outcome> evaluate(RemoteWorld world, List<Ranker> rankers, int stride) {
    Instant warm = RemoteWorld.START.plus(Duration.ofDays(WARMUP_DAYS));
    Map<Long, List<RemoteWorld.Read>> history = new HashMap<>();
    List<Outcome> outcomes = new ArrayList<>();
    for (RemoteWorld.Read read : world.reads) {
      if (!read.at().isBefore(warm) && read.seq() % stride == 0) {
        RemoteWorld.Reader reader = world.readers.get((int) read.readerId() - 1);
        Context context =
            context(world, reader, read, history.getOrDefault(reader.id(), List.of()));
        for (Ranker ranker : rankers) {
          outcomes.add(outcome(world, context, ranker.name(), ranker.rank().apply(context), read));
        }
        outcomes.add(outcome(world, context, CEILING, ceiling(context), read));
      }
      history.computeIfAbsent(read.readerId(), id -> new ArrayList<>()).add(read);
    }
    return outcomes;
  }

  private static Context context(
      RemoteWorld world,
      RemoteWorld.Reader reader,
      RemoteWorld.Read read,
      List<RemoteWorld.Read> before) {
    Instant now = read.at();
    List<RemoteWorld.Post> pool = world.pool(now, reader);
    Set<Long> seen = new HashSet<>();
    before.forEach(earlier -> seen.add(earlier.postId()));
    Map<Counts, List<ForYouCandidate>> candidates = new LinkedHashMap<>();
    for (Counts counts : Counts.values()) {
      List<ForYouCandidate> list = new ArrayList<>(pool.size());
      for (RemoteWorld.Post post : pool) {
        list.add(candidate(post, observed(post, now, counts)));
      }
      candidates.put(counts, list);
    }
    return new Context(
        world,
        reader,
        read.seq(),
        now,
        pool,
        seen,
        profile(world, reader, now, before),
        candidates);
  }

  static ForYouCandidate candidate(RemoteWorld.Post post, long reach) {
    return new ForYouCandidate(
        post.id(),
        uri(post.id()),
        post.author(),
        RemoteWorld.HOSTS.get(post.host()).name(),
        post.language(),
        post.tags(),
        post.createdAt(),
        reach,
        0,
        0,
        post.id());
  }

  static String uri(long postId) {
    return "p" + postId;
  }

  // What kurl last read of a post's count: when it was kept, at each check after that if they are
  // asked again, and every half hour while it is listed as trending.
  static long observed(RemoteWorld.Post post, Instant now, Counts counts) {
    Instant last = post.keptAt();
    if (counts == Counts.ASKED_AGAIN) {
      for (Duration check : CHECKS) {
        Instant at = post.keptAt().plus(check);
        if (!at.isAfter(now)) {
          last = at;
        }
      }
    }
    if (post.trended()) {
      Instant listedUntil = post.keptAt().plus(TRENDING_FOR);
      Instant refresh = now.isBefore(listedUntil) ? now : listedUntil;
      long steps =
          Duration.between(post.keptAt(), refresh).toSeconds()
              / RemoteWorld.TREND_REFRESH.toSeconds();
      Instant listed = post.keptAt().plus(RemoteWorld.TREND_REFRESH.multipliedBy(steps));
      last = listed.isAfter(last) ? listed : last;
    }
    return post.reachAt(last);
  }

  private static ForYouReader profile(
      RemoteWorld world, RemoteWorld.Reader reader, Instant now, List<RemoteWorld.Read> before) {
    Instant since = now.minus(SIGNAL_WINDOW);
    List<ForYouProfile.Signal> signals = new ArrayList<>();
    Map<String, Integer> perKind = new HashMap<>();
    for (int i = before.size() - 1; i >= 0; i--) {
      RemoteWorld.Read read = before.get(i);
      if (read.at().isBefore(since)) {
        break;
      }
      RemoteWorld.Post post = world.byId.get(read.postId());
      add(signals, perKind, "liked", read.liked(), ForYouProfile.LIKED, post);
      add(signals, perKind, "bookmarked", read.bookmarked(), ForYouProfile.BOOKMARKED, post);
      add(signals, perKind, "answered", read.answered(), ForYouProfile.ANSWERED, post);
      add(signals, perKind, "dwelt", read.dwelt(), ForYouProfile.DWELT, post);
    }
    int followed = 0;
    for (int i = world.posts.size() - 1; i >= 0 && followed < PER_KIND; i--) {
      RemoteWorld.Post post = world.posts.get(i);
      if (!post.keptAt().isAfter(now)
          && !post.createdAt().isBefore(since)
          && reader.followed().contains(post.author())) {
        signals.add(
            new ForYouProfile.Signal(
                ForYouProfile.FOLLOWED, post.author(), post.language(), post.tags()));
        followed++;
      }
    }
    return ForYouProfile.of(List.of(reader.locale()), signals);
  }

  private static void add(
      List<ForYouProfile.Signal> signals,
      Map<String, Integer> perKind,
      String kind,
      boolean happened,
      double weight,
      RemoteWorld.Post post) {
    if (happened && perKind.merge(kind, 1, Integer::sum) <= PER_KIND) {
      signals.add(new ForYouProfile.Signal(weight, post.author(), post.language(), post.tags()));
    }
  }

  private static List<Long> ceiling(Context context) {
    Map<String, Integer> byAuthor = new HashMap<>();
    for (Long postId : context.seen()) {
      byAuthor.merge(context.world().byId.get(postId).author(), 1, Integer::sum);
    }
    record Scored(long id, double score) {}
    List<Scored> unseen = new ArrayList<>();
    List<Scored> seen = new ArrayList<>();
    for (RemoteWorld.Post post : context.pool()) {
      double score =
          context
              .world()
              .spec
              .logit(
                  context.reader(), post, context.now(), byAuthor.getOrDefault(post.author(), 0));
      (context.seen().contains(post.id()) ? seen : unseen).add(new Scored(post.id(), score));
    }
    Comparator<Scored> best =
        Comparator.comparingDouble(Scored::score).reversed().thenComparingLong(Scored::id);
    unseen.sort(best);
    seen.sort(best);
    List<Long> ranked = new ArrayList<>();
    unseen.forEach(scored -> ranked.add(scored.id()));
    seen.forEach(scored -> ranked.add(scored.id()));
    return ranked;
  }

  private static Outcome outcome(
      RemoteWorld world, Context context, String ranker, List<Long> ranked, RemoteWorld.Read read) {
    int rank = ranked.indexOf(read.postId()) + 1;
    List<Long> top10 = ranked.subList(0, Math.min(10, ranked.size()));
    List<Long> top20 = ranked.subList(0, Math.min(20, ranked.size()));
    long readable =
        top10.stream()
            .filter(id -> context.reader().readable().contains(world.byId.get(id).language()))
            .count();
    Set<String> authors = new HashSet<>();
    Map<Integer, Integer> hosts = new HashMap<>();
    for (Long id : top20) {
      RemoteWorld.Post post = world.byId.get(id);
      authors.add(post.author());
      hosts.merge(post.host(), 1, Integer::sum);
    }
    int busiest = hosts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
    return new Outcome(
        ranker,
        read.seq(),
        read.at(),
        rank,
        top10.isEmpty() ? 0 : (double) readable / top10.size(),
        authors.size(),
        top20.isEmpty() ? 0 : (double) busiest / top20.size());
  }
}
