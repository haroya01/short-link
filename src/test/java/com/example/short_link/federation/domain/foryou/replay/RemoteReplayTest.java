package com.example.short_link.federation.domain.foryou.replay;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.federation.domain.foryou.ForYouCandidate;
import com.example.short_link.federation.domain.foryou.ForYouRanking;
import com.example.short_link.federation.domain.foryou.ForYouRanking.Weights;
import com.example.short_link.federation.domain.foryou.ForYouReader;
import com.example.short_link.federation.domain.foryou.replay.RemoteReplay.Context;
import com.example.short_link.federation.domain.foryou.replay.RemoteReplay.Counts;
import com.example.short_link.federation.domain.foryou.replay.RemoteReplay.Outcome;
import com.example.short_link.federation.domain.foryou.replay.RemoteReplay.Ranker;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

// The score contract: every world's metrics per ranker, compared with the committed baseline as
// the query contracts are. A ranking change that moves a number fails here; when it is meant,
// record again with REMOTE_REPLAY_RECORD=true and put the diff in the PR.
class RemoteReplayTest {

  static final long SEED = 20_261_022L;
  static final int DAYS = 30;
  static final int CUT_DAYS = 22;
  static final Path BASELINE = Path.of("src/test/resources/remote-for-you-replay/baseline.metrics");
  static final Path REPORT = Path.of("build/reports/remote-for-you-replay/baseline.md");
  static final String HEADER =
      "# world | ranker | evaluations | hit@10 | mrr@20 | ndcg@10 | lang@10 | authors@20"
          + " | maxHost@20";

  private static final Weights V1 = Weights.DEFAULT;

  static List<Ranker> rankers() {
    return List.of(
        new Ranker("random", RemoteReplayTest::random),
        new Ranker("latest", RemoteReplayTest::latest),
        new Ranker(
            "latest-diverse",
            context ->
                forYou(
                    context,
                    Counts.AT_KEEP,
                    new ForYouReader(context.profile().languages(), Map.of(), Map.of()),
                    new Weights(0, 1_000, 0, Duration.ofHours(48), 20, 2, 10))),
        new Ranker("reactions-only", RemoteReplayTest::reactionsOnly),
        new Ranker(
            "for-you-v1-no-recheck",
            context -> forYou(context, Counts.AT_KEEP, context.profile(), V1)),
        new Ranker(
            "for-you-v1", context -> forYou(context, Counts.ASKED_AGAIN, context.profile(), V1)),
        new Ranker(
            "v1-without-language",
            context ->
                forYou(
                    context,
                    Counts.ASKED_AGAIN,
                    context.profile(),
                    new Weights(
                        V1.reaction(),
                        0,
                        V1.interest(),
                        V1.freshness(),
                        V1.block(),
                        V1.authorSlots(),
                        V1.hostSlots()))),
        new Ranker(
            "v1-without-interest",
            context ->
                forYou(
                    context,
                    Counts.ASKED_AGAIN,
                    context.profile(),
                    new Weights(
                        V1.reaction(),
                        V1.language(),
                        0,
                        V1.freshness(),
                        V1.block(),
                        V1.authorSlots(),
                        V1.hostSlots()))),
        new Ranker(
            "v1-without-mixing",
            context ->
                forYou(
                    context,
                    Counts.ASKED_AGAIN,
                    context.profile(),
                    new Weights(
                        V1.reaction(),
                        V1.language(),
                        V1.interest(),
                        V1.freshness(),
                        V1.block(),
                        V1.block(),
                        V1.block()))),
        new Ranker("v1-server-relative", RemoteReplayTest::serverRelative));
  }

  private static List<Long> ids(List<ForYouCandidate> ranked) {
    return ranked.stream().map(candidate -> (Long) candidate.ref()).toList();
  }

  private static Set<String> seenUris(Context context) {
    return context.seen().stream().map(RemoteReplay::uri).collect(Collectors.toSet());
  }

  static List<Long> forYou(Context context, Counts counts, ForYouReader reader, Weights weights) {
    return ids(
        ForYouRanking.rank(
                context.candidates().get(counts), reader, seenUris(context), context.now(), weights)
            .items());
  }

  private static List<Long> random(Context context) {
    List<Long> ids = new ArrayList<>(context.pool().stream().map(RemoteWorld.Post::id).toList());
    Collections.shuffle(ids, new Random(context.readSeq()));
    return ids;
  }

  private static List<Long> unseenFirst(Context context, Comparator<RemoteWorld.Post> order) {
    List<RemoteWorld.Post> sorted = new ArrayList<>(context.pool());
    sorted.sort(
        Comparator.<RemoteWorld.Post, Boolean>comparing(post -> context.seen().contains(post.id()))
            .thenComparing(order));
    return sorted.stream().map(RemoteWorld.Post::id).toList();
  }

  private static List<Long> latest(Context context) {
    return unseenFirst(
        context,
        Comparator.comparing(RemoteWorld.Post::keptAt)
            .reversed()
            .thenComparing(Comparator.comparingLong(RemoteWorld.Post::id).reversed()));
  }

  private static List<Long> reactionsOnly(Context context) {
    Map<Long, Long> reach = new HashMap<>();
    context.candidates().get(Counts.AT_KEEP).forEach(c -> reach.put((Long) c.ref(), c.likes()));
    return unseenFirst(
        context,
        Comparator.<RemoteWorld.Post>comparingLong(post -> reach.get(post.id()))
            .reversed()
            .thenComparing(Comparator.comparingLong(RemoteWorld.Post::id).reversed()));
  }

  // Each server's counts scaled by its median over the pool, so a big server's ordinary post
  // weighs what a small server's ordinary post does.
  private static List<Long> serverRelative(Context context) {
    List<ForYouCandidate> candidates = context.candidates().get(Counts.ASKED_AGAIN);
    Map<String, List<Long>> byHost = new HashMap<>();
    candidates.forEach(
        c -> byHost.computeIfAbsent(c.host(), h -> new ArrayList<>()).add(c.likes()));
    List<Long> all = candidates.stream().map(ForYouCandidate::likes).sorted().toList();
    double overall = Math.max(1, median(all));
    Map<String, Double> scale = new HashMap<>();
    byHost.forEach((host, counts) -> scale.put(host, overall / Math.max(1, median(counts))));
    List<ForYouCandidate> scaled = new ArrayList<>(candidates.size());
    for (ForYouCandidate c : candidates) {
      scaled.add(
          new ForYouCandidate(
              c.ref(),
              c.uri(),
              c.author(),
              c.host(),
              c.language(),
              c.tags(),
              c.createdAt(),
              StrictMath.round(c.likes() * scale.get(c.host())),
              0,
              0,
              c.order()));
    }
    return ids(
        ForYouRanking.rank(scaled, context.profile(), seenUris(context), context.now(), V1)
            .items());
  }

  private static double median(List<Long> values) {
    List<Long> sorted = values.stream().sorted().toList();
    return sorted.isEmpty() ? 0 : sorted.get(sorted.size() / 2);
  }

  record Row(
      String world,
      String ranker,
      int evaluations,
      double hit10,
      double mrr20,
      double ndcg10,
      double lang10,
      double authors20,
      double maxHost20) {

    String line() {
      return String.format(
          Locale.ROOT,
          "%s | %s | %d | %.4f | %.4f | %.4f | %.4f | %.4f | %.4f",
          world,
          ranker,
          evaluations,
          hit10,
          mrr20,
          ndcg10,
          lang10,
          authors20,
          maxHost20);
    }
  }

  static List<Row> rows(String world, List<Outcome> outcomes) {
    Map<String, List<Outcome>> byRanker = new LinkedHashMap<>();
    outcomes.forEach(o -> byRanker.computeIfAbsent(o.ranker(), r -> new ArrayList<>()).add(o));
    List<Row> rows = new ArrayList<>();
    byRanker.forEach(
        (ranker, list) -> {
          double n = list.size();
          rows.add(
              new Row(
                  world,
                  ranker,
                  list.size(),
                  list.stream().filter(o -> o.rank() >= 1 && o.rank() <= 10).count() / n,
                  list.stream()
                          .mapToDouble(o -> o.rank() >= 1 && o.rank() <= 20 ? 1.0 / o.rank() : 0)
                          .sum()
                      / n,
                  list.stream()
                          .mapToDouble(
                              o ->
                                  o.rank() >= 1 && o.rank() <= 10
                                      ? 1 / (StrictMath.log(o.rank() + 1) / StrictMath.log(2))
                                      : 0)
                          .sum()
                      / n,
                  list.stream().mapToDouble(Outcome::lang10).sum() / n,
                  list.stream().mapToDouble(Outcome::authors20).sum() / n,
                  list.stream().mapToDouble(Outcome::maxHost20).sum() / n));
        });
    return rows;
  }

  record Prediction(String claim, Predicate<Map<String, Row>> holds) {}

  private static Row row(Map<String, Row> table, String world, String ranker) {
    return table.get(world + "/" + ranker);
  }

  // Registered before the first recording; a miss is reported, not failed.
  static final List<Prediction> PREDICTIONS =
      List.of(
          new Prediction(
              "P1 for-you-v1 hit@10 > latest in every world",
              t ->
                  WorldSpec.ALL.stream()
                      .allMatch(
                          w ->
                              row(t, w.name(), "for-you-v1").hit10()
                                  > row(t, w.name(), "latest").hit10())),
          new Prediction(
              "P2a asking again adds >= 0.02 hit@10 in reaction-led",
              t ->
                  row(t, "reaction-led", "for-you-v1").hit10()
                          - row(t, "reaction-led", "for-you-v1-no-recheck").hit10()
                      >= 0.02),
          new Prediction(
              "P2b asking again moves topical hit@10 by less than 0.01",
              t ->
                  Math.abs(
                          row(t, "topical", "for-you-v1").hit10()
                              - row(t, "topical", "for-you-v1-no-recheck").hit10())
                      < 0.01),
          new Prediction(
              "P3 authors@20: for-you-v1 >= 10 everywhere, latest <= 6 in reaction-led",
              t ->
                  WorldSpec.ALL.stream()
                          .allMatch(w -> row(t, w.name(), "for-you-v1").authors20() >= 10)
                      && row(t, "reaction-led", "latest").authors20() <= 6),
          new Prediction(
              "P4 maxHost@20: for-you-v1 <= 0.5 everywhere",
              t ->
                  WorldSpec.ALL.stream()
                      .allMatch(w -> row(t, w.name(), "for-you-v1").maxHost20() <= 0.5)),
          new Prediction(
              "P5 dropping the language term costs >= 0.2 lang@10 in topical",
              t ->
                  row(t, "topical", "for-you-v1").lang10()
                          - row(t, "topical", "v1-without-language").lang10()
                      >= 0.2),
          new Prediction(
              "P6 server-relative counts lower maxHost@20 and cost <= 0.01 hit@10 everywhere",
              t ->
                  WorldSpec.ALL.stream()
                      .allMatch(
                          w ->
                              row(t, w.name(), "v1-server-relative").maxHost20()
                                      < row(t, w.name(), "for-you-v1").maxHost20()
                                  && row(t, w.name(), "for-you-v1").hit10()
                                          - row(t, w.name(), "v1-server-relative").hit10()
                                      <= 0.01)));

  @Test
  void theScoresMatchTheBaselineAndEveryWorldSitsBetweenItsFloorAndCeiling() throws IOException {
    List<Row> rows = new ArrayList<>();
    for (WorldSpec spec : WorldSpec.ALL) {
      RemoteWorld world = RemoteWorld.generate(spec, DAYS, SEED);
      rows.addAll(rows(spec.name(), RemoteReplay.evaluate(world, rankers())));
    }
    Map<String, Row> table = new LinkedHashMap<>();
    rows.forEach(row -> table.put(row.world() + "/" + row.ranker(), row));
    List<String> lines = new ArrayList<>(List.of(HEADER));
    rows.forEach(row -> lines.add(row.line()));
    writeReport(rows, table);

    if (Boolean.parseBoolean(System.getenv("REMOTE_REPLAY_RECORD"))) {
      Files.createDirectories(BASELINE.getParent());
      Files.write(BASELINE, lines);
    }
    assertThat(lines).containsExactlyElementsOf(Files.readAllLines(BASELINE));
    for (WorldSpec spec : WorldSpec.ALL) {
      assertThat(row(table, spec.name(), "random").hit10())
          .isLessThan(row(table, spec.name(), "for-you-v1").hit10());
      assertThat(row(table, spec.name(), "for-you-v1").hit10())
          .isLessThan(row(table, spec.name(), RemoteReplay.CEILING).hit10());
    }
  }

  @Test
  void aWorldCutShortScoresItsReadsExactlyAsTheWholeWorldDoes() {
    WorldSpec spec = WorldSpec.ALL.getFirst();
    List<Ranker> v1 =
        List.of(
            new Ranker(
                "for-you-v1",
                context -> forYou(context, Counts.ASKED_AGAIN, context.profile(), V1)));
    Instant cut = RemoteWorld.START.plus(Duration.ofDays(CUT_DAYS));

    List<Outcome> whole =
        RemoteReplay.evaluate(RemoteWorld.generate(spec, DAYS, SEED), v1).stream()
            .filter(outcome -> outcome.at().isBefore(cut))
            .toList();
    List<Outcome> cutShort = RemoteReplay.evaluate(RemoteWorld.generate(spec, CUT_DAYS, SEED), v1);

    assertThat(whole).isNotEmpty().isEqualTo(cutShort);
  }

  private static void writeReport(List<Row> rows, Map<String, Row> table) throws IOException {
    StringBuilder report = new StringBuilder("# Remote For You replay\n\n");
    report.append(HEADER.substring(2)).append('\n');
    rows.forEach(row -> report.append(row.line()).append('\n'));
    report.append("\n## Pre-registered predictions\n\n");
    for (Prediction prediction : PREDICTIONS) {
      report
          .append(prediction.holds().test(table) ? "held   " : "missed ")
          .append(prediction.claim())
          .append('\n');
    }
    Files.createDirectories(REPORT.getParent());
    Files.writeString(REPORT, report.toString());
  }

  @Test
  void theChosenServersNewestPostsLookLikeWhatWasKeptOnTheDayItWasMeasured() throws IOException {
    RemoteWorld world = RemoteWorld.generate(WorldSpec.ALL.getFirst(), 21, SEED);
    Instant at = RemoteWorld.START.plus(Duration.ofDays(20));
    List<RemoteWorld.Post> latest =
        world.posts.stream()
            .filter(post -> RemoteWorld.HOSTS.get(post.host()).chosen())
            .filter(post -> !post.keptAt().isAfter(at))
            .sorted(Comparator.comparing(RemoteWorld.Post::keptAt).reversed())
            .limit(200)
            .toList();
    Map<String, Long> authors =
        latest.stream()
            .collect(Collectors.groupingBy(RemoteWorld.Post::author, Collectors.counting()));
    long top = authors.values().stream().mapToLong(Long::longValue).max().orElse(0);
    Map<String, Long> hosts =
        latest.stream()
            .collect(
                Collectors.groupingBy(
                    post -> RemoteWorld.HOSTS.get(post.host()).name(),
                    TreeMap::new,
                    Collectors.counting()));
    Map<String, Long> languages =
        latest.stream()
            .collect(
                Collectors.groupingBy(
                    RemoteWorld.Post::language, TreeMap::new, Collectors.counting()));
    long pool =
        world.posts.stream()
            .filter(post -> !post.keptAt().isAfter(at))
            .filter(post -> !post.createdAt().isBefore(at.minus(RemoteWorld.WINDOW)))
            .count();
    String calibration =
        "authors %d, top author share %.2f%nservers %s%nlanguages %s%npool %d, reads %d%n"
            .formatted(
                top == 0 ? 0 : authors.size(),
                top / 200.0,
                hosts,
                languages,
                pool,
                world.reads.size());
    Files.createDirectories(REPORT.getParent());
    Files.writeString(REPORT.resolveSibling("calibration.txt"), calibration);

    assertThat(latest).hasSize(200);
    assertThat(top / 200.0).isBetween(0.15, 0.45);
    assertThat(hosts.get("social.silicon.moe") / 200.0).isBetween(0.4, 0.7);
  }
}
