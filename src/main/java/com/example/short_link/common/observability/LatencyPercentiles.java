package com.example.short_link.common.observability;

public final class LatencyPercentiles {
  private LatencyPercentiles() {}

  public static double percentile(long[] sorted, double p) {
    if (sorted.length == 0) return 0.0;
    if (sorted.length == 1) return sorted[0];
    double rank = (sorted.length - 1) * p;
    int lo = (int) Math.floor(rank);
    int hi = (int) Math.ceil(rank);
    if (lo == hi) return sorted[lo];
    return sorted[lo] + (rank - lo) * (sorted[hi] - sorted[lo]);
  }
}
