package com.example.short_link.admin.application.read;

import com.example.short_link.admin.application.dto.AdminRouteMetric;
import com.example.short_link.common.observability.RequestMetricEntity;
import com.example.short_link.common.observability.RequestMetricJpaRepository;
import com.example.short_link.common.observability.RequestRouteMetrics;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class AdminRouteMetricsService {

  /** 전체 요청을 메모리에서 정렬하므로 집계 기간을 제한한다. */
  private static final Duration LIFETIME_CAP = Duration.ofDays(7);

  private final RequestMetricJpaRepository repository;
  private final Clock clock;

  public AdminRouteMetricsService(RequestMetricJpaRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  public List<AdminRouteMetric> routeMetrics() {
    return aggregate(LIFETIME_CAP);
  }

  public List<AdminRouteMetric> routeMetricsWindow(Window window) {
    return aggregate(window.duration());
  }

  private List<AdminRouteMetric> aggregate(Duration window) {
    Instant now = clock.instant();
    Instant from = now.minus(window);
    List<RequestMetricEntity> rows = repository.findWindow(from, now);
    List<AdminRouteMetric> out = new ArrayList<>();
    for (RequestRouteMetrics.Summary summary : RequestRouteMetrics.summarize(rows)) {
      out.add(
          new AdminRouteMetric(
              summary.route(),
              summary.method(),
              summary.count(),
              summary.p50(),
              summary.p95(),
              summary.p99(),
              summary.errorRate(),
              summary.error5xxCount(),
              new LinkedHashMap<>(summary.statusDistribution())));
    }
    return out;
  }

  public enum Window {
    H1(Duration.ofHours(1)),
    H24(Duration.ofHours(24)),
    D7(Duration.ofDays(7));

    private final Duration duration;

    Window(Duration duration) {
      this.duration = duration;
    }

    public Duration duration() {
      return duration;
    }

    public static Window parse(String raw) {
      if (raw == null) return H1;
      String s = raw.trim().toLowerCase(Locale.ROOT);
      return switch (s) {
        case "1h", "h1" -> H1;
        case "24h", "h24", "1d", "d1" -> H24;
        case "7d", "d7", "week" -> D7;
        default -> H1;
      };
    }
  }
}
