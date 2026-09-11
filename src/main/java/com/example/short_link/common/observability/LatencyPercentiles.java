package com.example.short_link.common.observability;

/** 지연 시간 표본의 백분위 계산. 정렬과 화면별 반올림은 호출자가 담당한다. */
public final class LatencyPercentiles {
  private LatencyPercentiles() {}

  /** 정렬된 표본에서 (n-1) * p 위치의 인접 값들을 선형 보간한다. */
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
