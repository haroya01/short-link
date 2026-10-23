package com.example.short_link.federation.domain.foryou.replay;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "REMOTE_REPLAY_POWER", matches = "true")
class RemoteReplayPowerTest {

  static final Path REPORT = Path.of("build/reports/remote-for-you-replay/power.md");
  static final int RESAMPLES = 2_000;
  static final long RESAMPLE_SEED = 20_261_023L;

  static final List<List<String>> PAIRS =
      List.of(
          List.of("for-you-v1", "latest"),
          List.of("for-you-v1", "for-you-v1-no-recheck"),
          List.of("v1-without-language", "for-you-v1"),
          List.of("v1-without-interest", "for-you-v1"),
          List.of("v1-without-mixing", "for-you-v1"),
          List.of("v1-server-relative", "for-you-v1"));

  record Comparison(
      String world,
      String a,
      String b,
      int reads,
      int readers,
      double mean,
      double readHalf,
      double low,
      double high,
      double p) {}

  static List<Comparison> compare(
      RemoteWorld world, String name, List<RemoteReplay.Outcome> outcomes) {
    Map<Long, Long> readerOf = new HashMap<>();
    world.reads.forEach(read -> readerOf.put(read.seq(), read.readerId()));
    Map<String, Map<Long, Integer>> hits = new HashMap<>();
    for (RemoteReplay.Outcome o : outcomes) {
      hits.computeIfAbsent(o.ranker(), r -> new LinkedHashMap<>())
          .put(o.readSeq(), o.rank() >= 1 && o.rank() <= 10 ? 1 : 0);
    }
    List<Comparison> out = new ArrayList<>();
    for (List<String> pair : PAIRS) {
      Map<Long, Integer> a = hits.get(pair.get(0));
      Map<Long, Integer> b = hits.get(pair.get(1));
      Map<Long, double[]> byReader = new LinkedHashMap<>();
      double sum = 0;
      double squares = 0;
      for (Long read : a.keySet()) {
        int d = a.get(read) - b.get(read);
        sum += d;
        squares += (double) d * d;
        double[] reader = byReader.computeIfAbsent(readerOf.get(read), r -> new double[2]);
        reader[0] += d;
        reader[1] += 1;
      }
      int n = a.size();
      double mean = sum / n;
      double variance = (squares - n * mean * mean) / (n - 1);
      double readHalf = 1.96 * StrictMath.sqrt(variance / n);
      double[][] readers = byReader.values().toArray(new double[0][]);
      double[] resampled = new double[RESAMPLES];
      SplittableRandom random = new SplittableRandom(RESAMPLE_SEED);
      for (int i = 0; i < RESAMPLES; i++) {
        double total = 0;
        double count = 0;
        for (int k = 0; k < readers.length; k++) {
          double[] drawn = readers[random.nextInt(readers.length)];
          total += drawn[0];
          count += drawn[1];
        }
        resampled[i] = total / count;
      }
      Arrays.sort(resampled);
      long below = Arrays.stream(resampled).filter(x -> x <= 0).count();
      long above = Arrays.stream(resampled).filter(x -> x >= 0).count();
      double p = Math.min(1, 2.0 * (Math.min(below, above) + 1) / (RESAMPLES + 1));
      out.add(
          new Comparison(
              name,
              pair.get(0),
              pair.get(1),
              n,
              readers.length,
              mean,
              readHalf,
              resampled[(int) (0.025 * RESAMPLES)],
              resampled[(int) (0.975 * RESAMPLES) - 1],
              p));
    }
    return out;
  }

  static double[] holm(List<Comparison> comparisons) {
    Integer[] order = new Integer[comparisons.size()];
    for (int i = 0; i < order.length; i++) {
      order[i] = i;
    }
    Arrays.sort(order, (x, y) -> Double.compare(comparisons.get(x).p(), comparisons.get(y).p()));
    double[] adjusted = new double[order.length];
    double running = 0;
    for (int rank = 0; rank < order.length; rank++) {
      double value = Math.min(1, (order.length - rank) * comparisons.get(order[rank]).p());
      running = Math.max(running, value);
      adjusted[order[rank]] = running;
    }
    return adjusted;
  }

  @Test
  void everyReadAndReaderLevelIntervalsOnTheEvaluationSeed() throws IOException {
    StringBuilder report =
        new StringBuilder("# Remote For You replay: power (evaluation seed)\n\n")
            .append("hit@10 paired differences. read ±: normal interval treating reads as")
            .append(" independent. reader [low, high]: 95% percentile interval from ")
            .append(RESAMPLES)
            .append(" resamples of whole readers. Holm: p adjusted over every comparison of")
            .append(" that stride.\n");
    for (int stride : new int[] {RemoteReplay.STRIDE, 1}) {
      long started = System.nanoTime();
      List<Comparison> comparisons = new ArrayList<>();
      for (WorldSpec spec : WorldSpec.ALL) {
        RemoteWorld world =
            RemoteWorld.generate(spec, RemoteReplayTest.DAYS, RemoteReplayTest.SEED);
        comparisons.addAll(
            compare(
                world,
                spec.name(),
                RemoteReplay.evaluate(world, RemoteReplayTest.rankers(), stride)));
      }
      double seconds = (System.nanoTime() - started) / 1e9;
      double[] adjusted = holm(comparisons);
      report
          .append(String.format(Locale.ROOT, "%n## stride %d (%.1f s)%n%n", stride, seconds))
          .append(
              "| world | a − b | reads | readers | mean | read ± | reader [low, high] | p | Holm |\n")
          .append("|---|---|---|---|---|---|---|---|---|\n");
      for (int i = 0; i < comparisons.size(); i++) {
        Comparison c = comparisons.get(i);
        report.append(
            String.format(
                Locale.ROOT,
                "| %s | %s − %s | %d | %d | %+.4f | %.4f | [%+.4f, %+.4f] | %.4f | %.4f |%n",
                c.world(),
                c.a(),
                c.b(),
                c.reads(),
                c.readers(),
                c.mean(),
                c.readHalf(),
                c.low(),
                c.high(),
                c.p(),
                adjusted[i]));
        assertThat(c.low()).isLessThanOrEqualTo(c.mean());
        assertThat(c.high()).isGreaterThanOrEqualTo(c.mean());
      }
    }
    Files.createDirectories(REPORT.getParent());
    Files.writeString(REPORT, report.toString());
  }
}
