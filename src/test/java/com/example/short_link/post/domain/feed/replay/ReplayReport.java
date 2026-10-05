package com.example.short_link.post.domain.feed.replay;

import com.example.short_link.post.domain.feed.FeedRanking;
import java.util.Locale;
import java.util.Map;

final class ReplayReport {

  private ReplayReport() {}

  static String markdown(
      long seed, Map<String, SyntheticWorld> worlds, Map<String, ReplayEvaluator.Run> runs) {
    StringBuilder out = new StringBuilder();
    out.append("# Feed ranking replay\n\n");
    out.append(
        String.format(
            Locale.ROOT,
            "seed %d · %d days · %d authors · %d readers · warm-up %d days · page %d · For You pool"
                + " %d · excluded reads %d\n\n",
            seed,
            SyntheticWorld.DAYS,
            SyntheticWorld.AUTHORS,
            SyntheticWorld.READERS,
            ReplayEvaluator.WARMUP_DAYS,
            ReplayEvaluator.PAGE,
            FeedRanking.CANDIDATE_POOL_SIZE,
            FeedRanking.EXCLUDED_READS));
    worlds.forEach(
        (name, world) -> {
          ReplayEvaluator.Run run = runs.get(name);
          long discoverable =
              world.posts.stream().filter(SyntheticWorld.Post::discoverable).count();
          long inSeries =
              world.posts.stream().filter(p -> p.candidate().seriesId() != null).count();
          long botViews = world.views.stream().filter(SyntheticWorld.View::bot).count();
          out.append("## ").append(name).append("\n\n");
          out.append(
              String.format(
                  Locale.ROOT,
                  "weights: topic %.1f · language %.1f · freshness %.1f · quality %.1f · popularity"
                      + " %.1f · loyalty %.1f\n\nposts %d (discoverable %d, in a series %d) · reads"
                      + " %d · views %d (bots %.1f%%) · likes %d · average pool %.0f\n\n",
                  world.spec.topic(),
                  world.spec.language(),
                  world.spec.freshness(),
                  world.spec.quality(),
                  world.spec.popularity(),
                  world.spec.loyalty(),
                  world.posts.size(),
                  discoverable,
                  inSeries,
                  world.reads.size(),
                  world.views.size(),
                  100.0 * botViews / world.views.size(),
                  world.likes.size(),
                  run.averagePool()));
          out.append(
              "| ranker | evaluations | hit@10 | mrr@20 | ndcg@10 | coverage@10 | lang@10 |"
                  + " authors@10 | seen@10 | short@10 |\n");
          out.append("|---|---|---|---|---|---|---|---|---|---|\n");
          for (ReplayScore s : run.scores()) {
            out.append("| ").append(s.row()).append(" |\n");
          }
          out.append("\n");
        });
    return out.toString();
  }
}
