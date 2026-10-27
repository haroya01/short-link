package com.example.short_link.common.observability;

import static com.example.short_link.common.observability.LatencyPercentiles.percentile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class RequestRouteMetrics {

  private RequestRouteMetrics() {}

  public static List<Summary> summarize(List<RequestMetric> rows) {
    return summarize(rows, false);
  }

  public static List<Summary> summarizeWithOutcomes(List<RequestMetric> rows) {
    return summarize(rows, true);
  }

  private static List<Summary> summarize(List<RequestMetric> rows, boolean includeOutcomes) {
    Map<String, List<RequestMetric>> byRoute = new HashMap<>();
    for (RequestMetric row : rows) {
      byRoute.computeIfAbsent(row.method() + " " + row.route(), key -> new ArrayList<>()).add(row);
    }
    List<Summary> summaries = new ArrayList<>(byRoute.size());
    for (List<RequestMetric> group : byRoute.values()) {
      summaries.add(summarizeRoute(group, includeOutcomes));
    }
    summaries.sort(Comparator.comparingLong(Summary::count).reversed());
    return summaries;
  }

  private static Summary summarizeRoute(List<RequestMetric> group, boolean includeOutcomes) {
    long errors = 0;
    long[] latencies = new long[group.size()];
    Map<String, Long> statuses = new TreeMap<>();
    Map<String, Long> outcomes = new TreeMap<>();
    for (int i = 0; i < group.size(); i++) {
      RequestMetric row = group.get(i);
      latencies[i] = row.latencyMs();
      if (row.status() >= 500) errors++;
      statuses.merge(String.valueOf(row.status()), 1L, Long::sum);
      if (includeOutcomes) outcomes.merge(row.outcome(), 1L, Long::sum);
    }
    Arrays.sort(latencies);
    RequestMetric first = group.getFirst();
    return new Summary(
        first.method(),
        first.route(),
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
