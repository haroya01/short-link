package com.example.short_link.post.domain.feed.replay;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FeedRankingReplayTest {

  private static final long SEED = 20_261_001L;
  private static final Path SNAPSHOT = Path.of("src/test/resources/reco-replay/baseline.metrics");
  private static final Path REPORT = Path.of("build/reports/reco-replay/baseline.md");

  private final Map<String, SyntheticWorld> worlds = new LinkedHashMap<>();
  private final Map<String, ReplayEvaluator.Run> runs = new LinkedHashMap<>();

  @BeforeAll
  void replayEveryWorld() throws IOException {
    for (WorldSpec spec : WorldSpec.ALL) {
      SyntheticWorld world = SyntheticWorld.generate(spec, SEED);
      worlds.put(spec.name(), world);
      runs.put(spec.name(), ReplayEvaluator.evaluate(world, ReplayRankers.PRODUCTION));
    }
    Files.createDirectories(REPORT.getParent());
    Files.writeString(REPORT, ReplayReport.markdown(SEED, worlds, runs));
  }

  @Test
  void scoresMatchTheReviewedSnapshot() throws IOException {
    String actual =
        ReplayScore.snapshot(runs.values().stream().flatMap(r -> r.scores().stream()).toList());
    if ("true".equals(System.getenv("RECO_REPLAY_RECORD"))) {
      Files.createDirectories(SNAPSHOT.getParent());
      Files.writeString(SNAPSHOT, actual);
    }
    assertThat(actual)
        .as(
            "Ranking scores moved. If the change is intended, rerun with RECO_REPLAY_RECORD=true"
                + " and review the diff of %s",
            SNAPSHOT)
        .isEqualTo(Files.readString(SNAPSHOT));
  }

  @Test
  void theCeilingLeadsAndRandomTrailsInEveryWorld() {
    runs.forEach(
        (world, run) -> {
          Map<String, Double> hits = new LinkedHashMap<>();
          run.scores().forEach(s -> hits.put(s.ranker(), s.hit10()));
          double ceiling = hits.remove(ReplayEvaluator.CEILING);
          double random = hits.remove(ReplayRankers.RANDOM.name());
          assertThat(hits.values())
              .as("%s: hit@10 between random %.4f and the ceiling %.4f", world, random, ceiling)
              .allSatisfy(hit -> assertThat(hit).isGreaterThan(random).isLessThan(ceiling));
        });
  }

  @Test
  void rankersCannotSeeTheFuture() {
    SyntheticWorld world = worlds.get(WorldSpec.ALL.get(0).name());
    Instant cut = SyntheticWorld.START.plus(Duration.ofDays(80));

    List<ReplayEvaluator.Trace> full =
        ReplayEvaluator.evaluate(world, ReplayRankers.PRODUCTION, 40).traces().stream()
            .filter(t -> t.now().isBefore(cut))
            .toList();
    List<ReplayEvaluator.Trace> past =
        ReplayEvaluator.evaluate(world.truncatedAt(cut), ReplayRankers.PRODUCTION, 40).traces();

    assertThat(full).hasSizeGreaterThan(100);
    assertThat(past).isEqualTo(full);
  }

  @Test
  void theSameSeedBuildsTheSameWorld() {
    WorldSpec spec = WorldSpec.ALL.get(0);
    SyntheticWorld again = SyntheticWorld.generate(spec, SEED);
    SyntheticWorld first = worlds.get(spec.name());

    assertThat(again.posts).isEqualTo(first.posts);
    assertThat(again.reads).isEqualTo(first.reads);
    assertThat(again.views).isEqualTo(first.views);
    assertThat(again.likes).isEqualTo(first.likes);
  }
}
