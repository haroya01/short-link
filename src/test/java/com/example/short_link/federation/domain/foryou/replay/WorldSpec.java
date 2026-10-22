package com.example.short_link.federation.domain.foryou.replay;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

// Every world shares the servers, posts and readers' traits; only how readers choose differs.
record WorldSpec(
    String name,
    double topic,
    double language,
    double freshness,
    double quality,
    double popularity,
    double loyalty) {

  static final List<WorldSpec> ALL =
      List.of(
          new WorldSpec("reaction-led", 1.0, 3.0, 1.0, 0.3, 1.0, 0.0),
          new WorldSpec("topical", 3.0, 3.0, 1.0, 0.5, 0.2, 0.3),
          new WorldSpec("author-loyal", 1.5, 3.0, 0.7, 0.3, 0.3, 3.0),
          new WorldSpec("loose-language", 2.0, 0.5, 1.0, 0.5, 0.5, 0.3));

  static final double FRESHNESS_HOURS = 24.0;

  static final int LOYAL_AFTER_READS = 2;

  double logit(RemoteWorld.Reader reader, RemoteWorld.Post post, Instant at, int authorReads) {
    double score = topic * reader.affinity()[post.topic()];
    if (!reader.readable().contains(post.language())) {
      score -= language;
    }
    score -=
        freshness * Duration.between(post.createdAt(), at).toSeconds() / 3_600.0 / FRESHNESS_HOURS;
    score += quality * StrictMath.log(post.quality());
    score += popularity * StrictMath.log1p(post.reachAt(at));
    if (authorReads >= LOYAL_AFTER_READS) {
      score += loyalty;
    }
    return score;
  }
}
