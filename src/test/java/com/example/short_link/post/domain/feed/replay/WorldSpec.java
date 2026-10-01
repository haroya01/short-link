package com.example.short_link.post.domain.feed.replay;

import java.util.List;

// Every world shares the corpus and the readers; only these weights differ between worlds.
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
          new WorldSpec("topical", 3.0, 3.0, 1.0, 0.5, 0.0, 0.5),
          new WorldSpec("popular", 1.0, 2.0, 1.0, 1.0, 1.2, 0.0),
          new WorldSpec("loose-language", 3.0, 0.5, 1.0, 0.5, 0.0, 0.5),
          new WorldSpec("author-loyal", 1.5, 3.0, 0.7, 0.5, 0.0, 3.0));

  static final double FRESHNESS_DAYS = 14.0;

  static final int LOYAL_AFTER_READS = 2;

  double logit(
      SyntheticWorld.Reader reader,
      SyntheticWorld.Post post,
      double ageDays,
      int readsOfAuthorByReader,
      int recentReadsOfPost) {
    double score = topic * reader.affinity()[post.topic()] / reader.topAffinity();
    if (!reader.languages().contains(post.lang())) {
      score -= language;
    }
    score -= freshness * ageDays / FRESHNESS_DAYS;
    score += quality * StrictMath.log(post.quality());
    score += popularity * StrictMath.log1p(recentReadsOfPost);
    if (readsOfAuthorByReader >= LOYAL_AFTER_READS) {
      score += loyalty;
    }
    return score;
  }
}
