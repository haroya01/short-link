package com.example.short_link.common.observability;

import static com.example.short_link.common.observability.LatencyPercentiles.percentile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** 요청 표본을 method + route 단위로 집계한다. 조회 기간과 화면별 응답은 호출자가 결정한다. */
public final class RequestRouteMetrics {

  private RequestRouteMetrics() {}

  public static List<Summary> summarize(List<RequestMetricEntity> rows) {
    return summarize(rows, false);
  }

  public static List<Summary> summarizeWithOutcomes(List<RequestMetricEntity> rows) {
    return summarize(rows, true);
  }

  private static List<Summary> summarize(List<RequestMetricEntity> rows, boolean includeOutcomes) {
    Map<String, List<RequestMetricEntity>> byRoute = new HashMap<>();
    for (RequestMetricEntity row : rows) {
      byRoute
          .computeIfAbsent(row.getMethod() + " " + row.getRoute(), key -> new ArrayList<>())
          .add(row);
    }
    List<Summary> summaries = new ArrayList<>(byRoute.size());
    for (List<RequestMetricEntity> group : byRoute.values()) {
      summaries.add(summarizeRoute(group, includeOutcomes));
    }
    summaries.sort(Comparator.comparingLong(Summary::count).reversed());
    return summaries;
  }

  private static Summary summarizeRoute(List<RequestMetricEntity> group, boolean includeOutcomes) {
    long errors = 0;
    long[] latencies = new long[group.size()];
    Map<String, Long> statuses = new TreeMap<>();
    Map<String, Long> outcomes = new TreeMap<>();
    for (int i = 0; i < group.size(); i++) {
      RequestMetricEntity row = group.get(i);
      latencies[i] = row.getLatencyMs();
      if (row.getStatus() >= 500) errors++;
      statuses.merge(String.valueOf(row.getStatus()), 1L, Long::sum);
      // 경로 지연 통계만 필요한 소비자는 outcome 값이나 분포에 의존하지 않는다.
      if (includeOutcomes) outcomes.merge(row.getOutcome(), 1L, Long::sum);
    }
    Arrays.sort(latencies);
    RequestMetricEntity first = group.getFirst();
    return new Summary(
        first.getMethod(),
        first.getRoute(),
        group.size(),
        percentile(latencies, 0.5),
        percentile(latencies, 0.95),
        percentile(latencies, 0.99),
        (double) errors / group.size(),
        errors,
        statuses,
        outcomes);
  }

  public record Summary(
      String method,
      String route,
      long count,
      double p50,
      double p95,
      double p99,
      double errorRate,
      long error5xxCount,
      Map<String, Long> statusDistribution,
      Map<String, Long> outcomeDistribution) {}
}
