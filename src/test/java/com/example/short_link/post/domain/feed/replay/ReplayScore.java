package com.example.short_link.post.domain.feed.replay;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

record ReplayScore(
    String world,
    String ranker,
    int evaluations,
    double hit10,
    double mrr20,
    double ndcg10,
    double coverage10,
    double language10,
    double authors10,
    double seen10,
    double short10) {

  static final String HEADER =
      "# world | ranker | evaluations | hit@10 | mrr@20 | ndcg@10 | coverage@10 | lang@10"
          + " | authors@10 | seen@10 | short@10";

  static String snapshot(List<ReplayScore> scores) {
    return scores.stream()
        .map(ReplayScore::line)
        .collect(Collectors.joining("\n", HEADER + "\n", "\n"));
  }

  String line() {
    return world + " | " + row();
  }

  String row() {
    return String.join(
        " | ",
        ranker,
        Integer.toString(evaluations),
        format(hit10),
        format(mrr20),
        format(ndcg10),
        format(coverage10),
        format(language10),
        format(authors10),
        format(seen10),
        format(short10));
  }

  static String format(double value) {
    return String.format(Locale.ROOT, "%.4f", value);
  }
}
