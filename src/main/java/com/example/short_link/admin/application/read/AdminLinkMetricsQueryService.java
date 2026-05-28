package com.example.short_link.admin.application.read;

import com.example.short_link.admin.application.dto.AdminLinkMetric;
import com.example.short_link.admin.domain.repository.AdminMetricsRepository;
import com.example.short_link.admin.domain.repository.AdminMetricsRepository.LinkMetricRow;
import com.example.short_link.common.observability.RequestMetricEntity;
import com.example.short_link.common.observability.RequestMetricJpaRepository;
import com.example.short_link.link.domain.ShortCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AdminLinkMetricsQueryService {

  private final RequestMetricJpaRepository requestMetricRepository;
  private final AdminMetricsRepository metricsRepository;
  private final Clock clock;

  @Autowired
  public AdminLinkMetricsQueryService(
      RequestMetricJpaRepository requestMetricRepository,
      AdminMetricsRepository metricsRepository) {
    this(requestMetricRepository, metricsRepository, Clock.systemUTC());
  }

  AdminLinkMetricsQueryService(
      RequestMetricJpaRepository requestMetricRepository,
      AdminMetricsRepository metricsRepository,
      Clock clock) {
    this.requestMetricRepository = requestMetricRepository;
    this.metricsRepository = metricsRepository;
    this.clock = clock;
  }

  public List<AdminLinkMetric> linkMetrics(Window window, Sort sort) {
    Instant now = clock.instant();
    Instant from = now.minus(window.duration());
    Map<String, List<RequestMetricEntity>> byShortCode = new HashMap<>();
    for (RequestMetricEntity row : requestMetricRepository.findWindow(from, now)) {
      String code = row.getShortCode();
      if (code == null || code.isBlank()) continue;
      byShortCode.computeIfAbsent(code, k -> new ArrayList<>()).add(row);
    }
    if (byShortCode.isEmpty()) return List.of();

    List<LinkMetricRow> rows =
        metricsRepository.linkMetricRowsByShortCodes(toValidShortCodes(byShortCode.keySet()));
    Map<String, LinkMetricRow> rowByCode = new HashMap<>(rows.size());
    for (LinkMetricRow row : rows) {
      rowByCode.put(row.getShortCode(), row);
    }

    List<AdminLinkMetric> out = new ArrayList<>(byShortCode.size());
    for (Map.Entry<String, List<RequestMetricEntity>> entry : byShortCode.entrySet()) {
      String shortCode = entry.getKey();
      List<RequestMetricEntity> group = entry.getValue();
      Aggregate agg = aggregate(group);
      LinkMetricRow row = rowByCode.get(shortCode);
      String originalUrl = row == null ? null : row.getOriginalUrl();
      Long userId = row == null ? null : row.getUserId();
      String ownerEmail = row == null ? null : row.getOwnerEmail();
      long totalRedirects = row == null ? agg.count() : nullToZero(row.getTotalRedirects());
      Instant lastRedirectAt = row == null ? agg.lastAt() : row.getLastRedirectAt();
      if (lastRedirectAt == null) lastRedirectAt = agg.lastAt();
      out.add(
          new AdminLinkMetric(
              shortCode,
              originalUrl,
              userId,
              ownerEmail,
              totalRedirects,
              agg.count(),
              agg.p50Millis(),
              agg.p95Millis(),
              agg.p99Millis(),
              agg.errorRate(),
              agg.outcomeCounts(),
              lastRedirectAt));
    }
    out.sort(sort.comparator());
    return out;
  }

  private static Aggregate aggregate(List<RequestMetricEntity> rows) {
    long count = rows.size();
    long errors = 0;
    long[] latencies = new long[rows.size()];
    Instant lastAt = null;
    Map<String, Long> outcomes = new LinkedHashMap<>();
    for (int i = 0; i < rows.size(); i++) {
      RequestMetricEntity row = rows.get(i);
      latencies[i] = row.getLatencyMs();
      if (row.getStatus() >= 500) errors++;
      outcomes.merge(row.getOutcome(), 1L, Long::sum);
      if (lastAt == null || row.getOccurredAt().isAfter(lastAt)) {
        lastAt = row.getOccurredAt();
      }
    }
    Arrays.sort(latencies);
    return new Aggregate(
        count,
        roundToMillis(percentile(latencies, 0.5)),
        roundToMillis(percentile(latencies, 0.95)),
        roundToMillis(percentile(latencies, 0.99)),
        count == 0 ? 0.0 : (double) errors / (double) count,
        outcomes,
        lastAt);
  }

  private static long roundToMillis(double v) {
    return Math.round(v);
  }

  private static double percentile(long[] sorted, double p) {
    if (sorted.length == 0) return 0.0;
    if (sorted.length == 1) return sorted[0];
    double rank = (sorted.length - 1) * p;
    int lo = (int) Math.floor(rank);
    int hi = (int) Math.ceil(rank);
    if (lo == hi) return sorted[lo];
    return sorted[lo] + (rank - lo) * (sorted[hi] - sorted[lo]);
  }

  private static long nullToZero(Long v) {
    return v == null ? 0L : v;
  }

  private static List<ShortCode> toValidShortCodes(Iterable<String> rawCodes) {
    List<ShortCode> codes = new ArrayList<>();
    for (String raw : rawCodes) {
      try {
        codes.add(new ShortCode(raw));
      } catch (IllegalArgumentException ignored) {
        // Request metrics may contain legacy/unknown route labels; keep their aggregate row, but
        // skip DB enrichment because LinkEntity.shortCode is a typed ShortCode value.
      }
    }
    return codes;
  }

  private record Aggregate(
      long count,
      long p50Millis,
      long p95Millis,
      long p99Millis,
      double errorRate,
      Map<String, Long> outcomeCounts,
      Instant lastAt) {}

  public enum Window {
    H1(Duration.ofHours(1)),
    H24(Duration.ofHours(24)),
    D7(Duration.ofDays(7)),
    ALL(Duration.ofDays(7));

    private final Duration duration;

    Window(Duration duration) {
      this.duration = duration;
    }

    public Duration duration() {
      return duration;
    }

    public static Window parse(String raw) {
      if (raw == null) return H24;
      String s = raw.trim().toLowerCase(Locale.ROOT);
      return switch (s) {
        case "1h", "h1" -> H1;
        case "24h", "h24", "1d", "d1" -> H24;
        case "7d", "d7", "week" -> D7;
        case "all" -> ALL;
        default -> H24;
      };
    }
  }

  public enum Sort {
    COUNT(
        Comparator.comparingLong(AdminLinkMetric::windowedRedirects)
            .reversed()
            .thenComparing(AdminLinkMetric::shortCode)),
    LATENCY(
        Comparator.comparingLong(AdminLinkMetric::p95Millis)
            .reversed()
            .thenComparing(AdminLinkMetric::shortCode)),
    ERROR(
        Comparator.comparingDouble(AdminLinkMetric::errorRate)
            .reversed()
            .thenComparing(AdminLinkMetric::shortCode));

    private final Comparator<AdminLinkMetric> comparator;

    Sort(Comparator<AdminLinkMetric> comparator) {
      this.comparator = comparator;
    }

    Comparator<AdminLinkMetric> comparator() {
      return comparator;
    }

    public static Sort parse(String raw) {
      if (raw == null) return COUNT;
      String s = raw.trim().toLowerCase(Locale.ROOT);
      return switch (s) {
        case "latency", "p95" -> LATENCY;
        case "error", "errors" -> ERROR;
        default -> COUNT;
      };
    }
  }
}
