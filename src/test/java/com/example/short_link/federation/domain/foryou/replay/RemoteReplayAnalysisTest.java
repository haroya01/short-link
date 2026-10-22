package com.example.short_link.federation.domain.foryou.replay;

import com.example.short_link.federation.domain.foryou.ForYouCandidate;
import com.example.short_link.federation.domain.foryou.ForYouRanking;
import com.example.short_link.federation.domain.foryou.ForYouRanking.Weights;
import com.example.short_link.federation.domain.foryou.ForYouReader;
import com.example.short_link.federation.domain.foryou.replay.RemoteReplay.Context;
import com.example.short_link.federation.domain.foryou.replay.RemoteReplay.Counts;
import com.example.short_link.federation.domain.foryou.replay.RemoteReplay.Ranker;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

// Questions the baseline raised, asked of a tuning seed so the contract's seed stays for judging.
@EnabledIfEnvironmentVariable(named = "REMOTE_REPLAY_ANALYSIS", matches = "true")
class RemoteReplayAnalysisTest {

  static final long TUNING_SEED = 7L;
  static final Path REPORT = Path.of("build/reports/remote-for-you-replay/analysis.md");

  private static final Weights V1 = Weights.DEFAULT;

  private static List<Long> rank(
      Context context, List<ForYouCandidate> candidates, ForYouReader reader, Weights weights) {
    Set<String> seen = context.seen().stream().map(RemoteReplay::uri).collect(Collectors.toSet());
    return ForYouRanking.rank(candidates, reader, seen, context.now(), weights).items().stream()
        .map(candidate -> (Long) candidate.ref())
        .toList();
  }

  private static List<ForYouCandidate> perSixHours(Context context) {
    List<ForYouCandidate> paced = new ArrayList<>();
    for (ForYouCandidate c : context.candidates().get(Counts.ASKED_AGAIN)) {
      double hours = Duration.between(c.createdAt(), context.now()).toSeconds() / 3_600.0;
      paced.add(
          new ForYouCandidate(
              c.ref(),
              c.uri(),
              c.author(),
              c.host(),
              c.language(),
              c.tags(),
              c.createdAt(),
              StrictMath.round(c.likes() * 6 / (6 + hours)),
              0,
              0,
              c.order()));
    }
    return paced;
  }

  private static Weights freshness(Duration scale) {
    return new Weights(
        V1.reaction(),
        V1.language(),
        V1.interest(),
        scale,
        V1.block(),
        V1.authorSlots(),
        V1.hostSlots());
  }

  static List<Ranker> variants() {
    return List.of(
        new Ranker(
            "v1-no-recheck", c -> rank(c, c.candidates().get(Counts.AT_KEEP), c.profile(), V1)),
        new Ranker("v1", c -> rank(c, c.candidates().get(Counts.ASKED_AGAIN), c.profile(), V1)),
        new Ranker(
            "v1-tags-only",
            c ->
                rank(
                    c,
                    c.candidates().get(Counts.ASKED_AGAIN),
                    new ForYouReader(c.profile().languages(), c.profile().tags(), Map.of()),
                    V1)),
        new Ranker(
            "v1-fresh-24h",
            c ->
                rank(
                    c,
                    c.candidates().get(Counts.ASKED_AGAIN),
                    c.profile(),
                    freshness(Duration.ofHours(24)))),
        new Ranker("v1-pace", c -> rank(c, perSixHours(c), c.profile(), V1)),
        new Ranker(
            "v1-pace-tags-only",
            c ->
                rank(
                    c,
                    perSixHours(c),
                    new ForYouReader(c.profile().languages(), c.profile().tags(), Map.of()),
                    V1)));
  }

  private static final List<List<String>> PAIRS =
      List.of(
          List.of("v1", "v1-no-recheck"),
          List.of("v1-tags-only", "v1"),
          List.of("v1-fresh-24h", "v1"),
          List.of("v1-pace", "v1"),
          List.of("v1-pace-tags-only", "v1"));

  // The same reads ranked two ways: the mean difference in hit@10 and its 95% interval.
  static String paired(
      String world, List<RemoteReplay.Outcome> outcomes, List<List<String>> pairs) {
    Map<String, Map<Long, Integer>> hits = new java.util.HashMap<>();
    for (RemoteReplay.Outcome o : outcomes) {
      hits.computeIfAbsent(o.ranker(), r -> new java.util.HashMap<>())
          .put(o.readSeq(), o.rank() >= 1 && o.rank() <= 10 ? 1 : 0);
    }
    StringBuilder lines = new StringBuilder();
    for (List<String> pair : pairs) {
      Map<Long, Integer> a = hits.get(pair.get(0));
      Map<Long, Integer> b = hits.get(pair.get(1));
      double n = a.size();
      double mean = 0;
      for (Long read : a.keySet()) {
        mean += a.get(read) - b.get(read);
      }
      mean /= n;
      double squares = 0;
      for (Long read : a.keySet()) {
        double d = a.get(read) - b.get(read) - mean;
        squares += d * d;
      }
      double half = 1.96 * StrictMath.sqrt(squares / (n - 1) / n);
      lines.append(
          String.format(
              java.util.Locale.ROOT,
              "%s | %s − %s | %+.4f ± %.4f%n",
              world,
              pair.get(0),
              pair.get(1),
              mean,
              half));
    }
    return lines.toString();
  }

  @Test
  void variantsOnTheTuningSeed() throws IOException {
    StringBuilder report = new StringBuilder("# Remote For You analysis (tuning seed)\n\n");
    report.append(RemoteReplayTest.HEADER.substring(2)).append('\n');
    StringBuilder differences = new StringBuilder("\n## Paired hit@10 differences\n\n");
    for (WorldSpec spec : WorldSpec.ALL) {
      RemoteWorld world = RemoteWorld.generate(spec, RemoteReplayTest.DAYS, TUNING_SEED);
      List<RemoteReplay.Outcome> outcomes = RemoteReplay.evaluate(world, variants());
      for (RemoteReplayTest.Row row : RemoteReplayTest.rows(spec.name(), outcomes)) {
        report.append(row.line()).append('\n');
      }
      differences.append(paired(spec.name(), outcomes, PAIRS));
    }
    differences.append("\n## Paired differences on the evaluation seed\n\n");
    for (WorldSpec spec : WorldSpec.ALL) {
      RemoteWorld world = RemoteWorld.generate(spec, RemoteReplayTest.DAYS, RemoteReplayTest.SEED);
      differences.append(
          paired(
              spec.name(),
              RemoteReplay.evaluate(world, RemoteReplayTest.rankers()),
              List.of(
                  List.of("for-you-v1", "for-you-v1-no-recheck"),
                  List.of("for-you-v1", "latest"),
                  List.of("v1-without-interest", "for-you-v1"),
                  List.of("v1-without-mixing", "for-you-v1"),
                  List.of("v1-server-relative", "for-you-v1"))));
    }
    Files.createDirectories(REPORT.getParent());
    Files.writeString(REPORT, report.append(differences).toString());
  }
}
