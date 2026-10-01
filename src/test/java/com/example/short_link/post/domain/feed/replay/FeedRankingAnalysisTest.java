package com.example.short_link.post.domain.feed.replay;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.example.short_link.post.domain.feed.ForYouRanking;
import com.example.short_link.post.domain.feed.ForYouRanking.Weights;
import com.example.short_link.post.domain.feed.replay.ReplayEvaluator.Ranker;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

// Analysis, not a gate: runs only with RECO_REPLAY_ANALYSIS=true. Weights are swept on a separate
// tuning seed so the snapshot seed stays a held-out check.
class FeedRankingAnalysisTest {

  private static final long EVALUATION_SEED = 20_261_001L;
  private static final long TUNING_SEED = 7L;
  private static final Path REPORT = Path.of("build/reports/reco-replay/analysis.md");

  @Test
  void writeAblationsAndSensitivity() throws IOException {
    assumeTrue("true".equals(System.getenv("RECO_REPLAY_ANALYSIS")));
    StringBuilder out = new StringBuilder("# For You v1 analysis\n\n");

    Weights b = Weights.DEFAULT;
    Map<String, Weights> ablations = new LinkedHashMap<>();
    ablations.put("v1", b);
    ablations.put("- topic", weights(0, b.language(), b.freshness(), b.popularity(), true, true));
    ablations.put("- language", weights(b.topic(), 0, b.freshness(), b.popularity(), true, true));
    ablations.put(
        "- common-tag discount",
        weights(b.topic(), b.language(), b.freshness(), b.popularity(), false, true));
    ablations.put("- freshness", weights(b.topic(), b.language(), 0, b.popularity(), true, true));
    ablations.put("- popularity", weights(b.topic(), b.language(), b.freshness(), 0, true, true));
    ablations.put(
        "- author mixing",
        weights(b.topic(), b.language(), b.freshness(), b.popularity(), true, false));
    out.append("## Ablations (evaluation seed ").append(EVALUATION_SEED).append(")\n\n");
    table(out, EVALUATION_SEED, ablations, List.of(matchesOnly(b)));

    out.append("## Sensitivity (tuning seed ").append(TUNING_SEED).append(")\n\n");
    Map<String, Weights> sweep = new LinkedHashMap<>();
    for (double v : new double[] {1.5, 3.0, 6.0}) {
      sweep.put("topic " + v, weights(v, b.language(), b.freshness(), b.popularity(), true, true));
    }
    for (double v : new double[] {0, 0.75, 1.5, 3.0}) {
      sweep.put("language " + v, weights(b.topic(), v, b.freshness(), b.popularity(), true, true));
    }
    for (double v : new double[] {0, 0.5, 1.0, 2.0}) {
      sweep.put("freshness " + v, weights(b.topic(), b.language(), v, b.popularity(), true, true));
    }
    for (double v : new double[] {0, 0.3, 0.6, 1.2}) {
      sweep.put("popularity " + v, weights(b.topic(), b.language(), b.freshness(), v, true, true));
    }
    table(out, TUNING_SEED, sweep, List.of());

    Files.createDirectories(REPORT.getParent());
    Files.writeString(REPORT, out.toString());
  }

  private static void table(
      StringBuilder out, long seed, Map<String, Weights> variants, List<Ranker> extra) {
    List<Ranker> rankers = new ArrayList<>(List.of(ReplayRankers.FOR_YOU_V0));
    variants.forEach((name, weights) -> rankers.add(ReplayRankers.forYouV1(name, weights)));
    rankers.addAll(extra);
    Map<String, Map<String, ReplayScore>> byWorld = new LinkedHashMap<>();
    for (WorldSpec spec : WorldSpec.ALL) {
      Map<String, ReplayScore> scores = new LinkedHashMap<>();
      ReplayEvaluator.evaluate(SyntheticWorld.generate(spec, seed), rankers)
          .scores()
          .forEach(s -> scores.put(s.ranker(), s));
      byWorld.put(spec.name(), scores);
    }
    out.append("hit@10 (lang@10 · authors@10)\n\n| variant |");
    byWorld.keySet().forEach(world -> out.append(' ').append(world).append(" |"));
    out.append("\n|---|").append("---|".repeat(byWorld.size())).append('\n');
    for (Ranker ranker : rankers) {
      out.append("| ").append(ranker.name()).append(" |");
      byWorld
          .values()
          .forEach(
              scores -> {
                ReplayScore s = scores.get(ranker.name());
                out.append(' ')
                    .append(ReplayScore.format(s.hit10()))
                    .append(" (")
                    .append(
                        String.format(Locale.ROOT, "%.2f · %.2f", s.language10(), s.authors10()))
                    .append(") |");
              });
      out.append('\n');
    }
    out.append('\n');
  }

  // v1 order restricted to posts sharing an interest tag, as v0 did (all posts on a cold start).
  private static Ranker matchesOnly(Weights weights) {
    Ranker v1 = ReplayRankers.forYouV1("v1 matches only", weights);
    return new Ranker(
        v1.name(),
        context -> {
          ReplayEvaluator.Viewer viewer = context.viewer();
          Set<String> interest =
              ForYouRanking.Reader.of(
                      viewer.id(),
                      viewer.locale(),
                      viewer.followedTags(),
                      viewer.hiddenTags(),
                      viewer.readsNewestFirst(),
                      viewer.likesNewestFirst(),
                      context.catalog())
                  .interest()
                  .keySet();
          List<Long> ranked = v1.rank().apply(context);
          if (interest.isEmpty()) {
            return ranked;
          }
          return ranked.stream()
              .filter(
                  id ->
                      context.catalog().get(id).normalizedTags().stream()
                          .anyMatch(interest::contains))
              .toList();
        });
  }

  private static Weights weights(
      double topic,
      double language,
      double freshness,
      double popularity,
      boolean discountCommonTags,
      boolean mixAuthors) {
    return new Weights(topic, language, freshness, popularity, discountCommonTags, mixAuthors);
  }
}
