package com.example.short_link.post.domain.feed.replay;

import com.example.short_link.post.domain.feed.FeedCandidate;
import com.example.short_link.post.domain.feed.FeedRanking;
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

final class ReplayEvaluator {

  static final int PAGE = 20;
  static final int WARMUP_DAYS = 45;
  static final int MAX_EVALUATIONS = 3_000;
  static final String CEILING = "ceiling";

  // What a production ranker could know about the reader at that moment. Latent traits (topic
  // affinity, readable languages) stay out of it.
  record Viewer(
      long id,
      String locale,
      List<String> followedTags,
      List<String> hiddenTags,
      List<Long> readsNewestFirst,
      List<Long> likesNewestFirst) {}

  record Context(
      Viewer viewer,
      Instant now,
      List<FeedCandidate> poolNewestFirst,
      Map<Long, Long> recentViews,
      Map<Long, Long> recentHumanViews,
      Map<Long, FeedCandidate> catalog,
      long evaluation) {}

  record Ranker(String name, Function<Context, List<Long>> rank) {}

  record Trace(
      long evaluation, Instant now, long readerId, long target, Map<String, List<Long>> ranked) {}

  record Run(List<ReplayScore> scores, List<Trace> traces, double averagePool) {}

  private ReplayEvaluator() {}

  static Run evaluate(SyntheticWorld world, List<Ranker> rankers) {
    Instant warmupEnd = warmupEnd();
    long eligible = world.reads.stream().filter(r -> !r.at().isBefore(warmupEnd)).count();
    return evaluate(world, rankers, Math.max(1, eligible / MAX_EVALUATIONS));
  }

  static Run evaluate(SyntheticWorld world, List<Ranker> rankers, long stride) {
    Instant warmupEnd = warmupEnd();
    Map<Long, SyntheticWorld.Reader> readers = new HashMap<>();
    world.readers.forEach(r -> readers.put(r.id(), r));
    Map<Long, FeedCandidate> catalog = new HashMap<>();
    world.posts.forEach(p -> catalog.put(p.id(), p.candidate()));

    Map<String, Tally> tallies = new LinkedHashMap<>();
    rankers.forEach(r -> tallies.put(r.name(), new Tally()));
    tallies.put(CEILING, new Tally());
    List<Trace> traces = new ArrayList<>();

    SyntheticWorld.ReadingState state = new SyntheticWorld.ReadingState();
    List<SyntheticWorld.Post> pool = new ArrayList<>();
    Map<Long, List<Long>> readsByReader = new HashMap<>();
    Map<Long, List<Long>> likesByReader = new HashMap<>();
    Map<Long, Long> recentViews = new HashMap<>();
    Map<Long, Long> recentHumanViews = new HashMap<>();
    int nextPost = 0;
    int nextView = 0;
    int oldestView = 0;
    int nextLike = 0;
    long eligibleIndex = 0;
    long evaluation = 0;
    long poolSizes = 0;

    for (SyntheticWorld.Read read : world.reads) {
      Instant now = read.at();
      while (nextPost < world.posts.size()
          && !world.posts.get(nextPost).publishedAt().isAfter(now)) {
        SyntheticWorld.Post post = world.posts.get(nextPost++);
        if (post.discoverable()) pool.add(post);
      }
      while (nextView < world.views.size() && world.views.get(nextView).at().isBefore(now)) {
        count(world.views.get(nextView++), recentViews, recentHumanViews, 1);
      }
      Instant windowStart = now.minus(FeedRanking.TRENDING_WINDOW);
      while (oldestView < nextView && world.views.get(oldestView).at().isBefore(windowStart)) {
        count(world.views.get(oldestView++), recentViews, recentHumanViews, -1);
      }
      while (nextLike < world.likes.size() && world.likes.get(nextLike).at().isBefore(now)) {
        SyntheticWorld.Like like = world.likes.get(nextLike++);
        likesByReader.computeIfAbsent(like.readerId(), id -> new ArrayList<>()).add(like.postId());
      }
      state.forgetBefore(now.minus(SyntheticWorld.POPULARITY_WINDOW));

      if (!now.isBefore(warmupEnd) && eligibleIndex++ % stride == 0) {
        SyntheticWorld.Reader reader = readers.get(read.readerId());
        List<Long> readIds = readsByReader.getOrDefault(reader.id(), List.of());
        Viewer viewer =
            new Viewer(
                reader.id(),
                reader.languageOrder().get(0),
                reader.followedTags(),
                reader.hiddenTags(),
                newestFirst(readIds),
                newestFirst(likesByReader.getOrDefault(reader.id(), List.of())));
        List<FeedCandidate> poolNewestFirst =
            newestFirst(pool.stream().map(SyntheticWorld.Post::candidate).toList());
        Context context =
            new Context(
                viewer,
                now,
                poolNewestFirst,
                Map.copyOf(recentViews),
                Map.copyOf(recentHumanViews),
                catalog,
                evaluation);
        Set<Long> seen = new HashSet<>(readIds);
        Map<String, List<Long>> ranked = new LinkedHashMap<>();
        for (Ranker ranker : rankers) {
          ranked.put(ranker.name(), firstPage(ranker.rank().apply(context)));
        }
        ranked.put(CEILING, ceiling(world, state, reader, pool, now));
        ranked.forEach(
            (name, ids) -> tallies.get(name).add(ids, read.postId(), reader, seen, world));
        traces.add(new Trace(evaluation, now, reader.id(), read.postId(), ranked));
        poolSizes += pool.size();
        evaluation++;
      }

      state.record(read, world.postById.get(read.postId()));
      readsByReader.computeIfAbsent(read.readerId(), id -> new ArrayList<>()).add(read.postId());
    }

    long discoverable = world.posts.stream().filter(SyntheticWorld.Post::discoverable).count();
    List<ReplayScore> scores = new ArrayList<>();
    tallies.forEach(
        (name, tally) -> scores.add(tally.score(world.spec.name(), name, discoverable)));
    return new Run(scores, traces, evaluation == 0 ? 0 : (double) poolSizes / evaluation);
  }

  static Instant warmupEnd() {
    return SyntheticWorld.START.plus(Duration.ofDays(WARMUP_DAYS));
  }

  private static List<Long> ceiling(
      SyntheticWorld world,
      SyntheticWorld.ReadingState state,
      SyntheticWorld.Reader reader,
      List<SyntheticWorld.Post> pool,
      Instant now) {
    record Scored(long id, double logit) {}
    return pool.stream()
        .filter(p -> !state.hasRead(reader.id(), p.id()))
        .map(p -> new Scored(p.id(), state.logit(world.spec, reader, p, now)))
        .sorted(
            Comparator.comparingDouble(Scored::logit)
                .reversed()
                .thenComparing(Comparator.comparingLong(Scored::id).reversed()))
        .limit(PAGE)
        .map(Scored::id)
        .toList();
  }

  private static void count(
      SyntheticWorld.View view, Map<Long, Long> all, Map<Long, Long> human, long delta) {
    all.merge(view.postId(), delta, Long::sum);
    if (!view.bot()) {
      human.merge(view.postId(), delta, Long::sum);
    }
  }

  private static <T> List<T> newestFirst(List<T> oldestFirst) {
    return List.copyOf(oldestFirst.reversed());
  }

  private static List<Long> firstPage(List<Long> ranked) {
    return List.copyOf(ranked.subList(0, Math.min(PAGE, ranked.size())));
  }

  private static final class Tally {
    private int evaluations;
    private int hits;
    private double reciprocalRank;
    private double gain;
    private int filled;
    private double language;
    private double authors;
    private double seen;
    private int short10;
    private final Set<Long> shown = new HashSet<>();

    void add(
        List<Long> ranked,
        long target,
        SyntheticWorld.Reader reader,
        Set<Long> alreadyRead,
        SyntheticWorld world) {
      evaluations++;
      int rank = ranked.indexOf(target) + 1;
      if (rank > 0) {
        reciprocalRank += 1.0 / rank;
      }
      if (rank > 0 && rank <= 10) {
        hits++;
        gain += StrictMath.log(2) / StrictMath.log(rank + 1);
      }
      List<Long> top = ranked.subList(0, Math.min(10, ranked.size()));
      if (top.size() < 10) {
        short10++;
      }
      if (top.isEmpty()) {
        return;
      }
      filled++;
      Set<Long> authorIds = new HashSet<>();
      int sameLanguage = 0;
      int already = 0;
      for (long id : top) {
        SyntheticWorld.Post post = world.postById.get(id);
        authorIds.add(post.authorId());
        if (reader.languages().contains(post.lang())) sameLanguage++;
        if (alreadyRead.contains(id)) already++;
      }
      language += (double) sameLanguage / top.size();
      authors += (double) authorIds.size() / top.size();
      seen += (double) already / top.size();
      shown.addAll(top);
    }

    ReplayScore score(String world, String ranker, long discoverable) {
      return new ReplayScore(
          world,
          ranker,
          evaluations,
          ratio(hits, evaluations),
          ratio(reciprocalRank, evaluations),
          ratio(gain, evaluations),
          ratio(shown.size(), discoverable),
          ratio(language, filled),
          ratio(authors, filled),
          ratio(seen, filled),
          ratio(short10, evaluations));
    }

    private static double ratio(double part, double whole) {
      return whole == 0 ? 0 : part / whole;
    }
  }
}
