package com.example.short_link.admin.application;

import com.example.short_link.admin.application.AdminMetricsRepository.LinkMetricRow;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Per-{@code shortCode} performance view. Latency / outcome / error rate come from {@link
 * LinkMetricsRecorder}'s in-memory ring (bounded LRU — top-N hot links only). Lifetime totals,
 * original URL, owner email and last-clicked-at come from the DB so the table can still show codes
 * that haven't seen recent traffic.
 *
 * <p>This is intentionally NOT a Micrometer Timer per short code — that would explode tag
 * cardinality on the registry and break the {@code http.server.requests} histogram budget. The ring
 * lives in process memory with a hard cap so an unbounded short-code namespace can't OOM the pod.
 */
@Service
@RequiredArgsConstructor
public class AdminLinkMetricsService {

  private final LinkMetricsRecorder recorder;
  private final AdminMetricsRepository metricsRepository;

  public List<AdminLinkMetric> linkMetrics(Window window, Sort sort) {
    Map<String, List<LinkMetricsRecorder.Sample>> slice = recorder.sliceWindow(window.minutes());
    if (slice.isEmpty()) return List.of();
    List<LinkMetricRow> rows = metricsRepository.linkMetricRowsByShortCodes(slice.keySet());
    Map<String, LinkMetricRow> rowByCode = new HashMap<>(rows.size());
    for (LinkMetricRow row : rows) {
      rowByCode.put(row.getShortCode(), row);
    }

    List<AdminLinkMetric> out = new ArrayList<>(slice.size());
    for (Map.Entry<String, List<LinkMetricsRecorder.Sample>> entry : slice.entrySet()) {
      String shortCode = entry.getKey();
      LinkMetricsRecorder.Aggregate agg = LinkMetricsRecorder.aggregate(entry.getValue());
      LinkMetricRow row = rowByCode.get(shortCode);
      // DB miss is possible — link could have been deleted between the recorder tick and the read.
      // Surface the ring snapshot anyway so the operator at least sees the burst that happened.
      String originalUrl = row == null ? null : row.getOriginalUrl();
      Long userId = row == null ? null : row.getUserId();
      String ownerEmail = row == null ? null : row.getOwnerEmail();
      long totalRedirects = row == null ? agg.count() : nullToZero(row.getTotalRedirects());
      Instant lastRedirectAt = row == null ? agg.lastAt() : row.getLastRedirectAt();
      // when the DB row is missing entirely, lastRedirectAt falls back to the ring's newest sample
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

  private static long nullToZero(Long v) {
    return v == null ? 0L : v;
  }

  public enum Window {
    H1(60),
    H24(60 * 24),
    D7(60 * 24 * 7),
    ALL(60 * 24 * 7);

    private final int minutes;

    Window(int minutes) {
      this.minutes = minutes;
    }

    public int minutes() {
      return minutes;
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
