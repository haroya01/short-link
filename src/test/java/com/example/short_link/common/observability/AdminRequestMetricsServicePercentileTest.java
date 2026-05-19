package com.example.short_link.common.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class AdminRequestMetricsServicePercentileTest {

  @Test
  void emptyArrayReturnsZero() {
    assertThat(AdminRequestMetricsService.percentile(new long[0], 0.5)).isZero();
  }

  @Test
  void singletonReturnsTheElement() {
    assertThat(AdminRequestMetricsService.percentile(new long[] {42}, 0.99)).isEqualTo(42.0);
  }

  @Test
  void p50OnSortedRangeUsesLinearInterpolation() {
    long[] sorted = new long[] {10, 20, 30, 40, 50};
    // (n-1)*p = 4 * 0.5 = 2.0 → sorted[2] = 30
    assertThat(AdminRequestMetricsService.percentile(sorted, 0.5)).isEqualTo(30.0);
  }

  @Test
  void p95OnSortedRangeInterpolatesBetweenAdjacent() {
    long[] sorted = new long[] {10, 20, 30, 40, 50};
    // (n-1)*p = 4 * 0.95 = 3.8 → sorted[3] + 0.8 * (sorted[4] - sorted[3]) = 40 + 8 = 48
    assertThat(AdminRequestMetricsService.percentile(sorted, 0.95)).isCloseTo(48.0, within(0.0001));
  }

  @Test
  void p99OnSortedRangeApproachesMax() {
    long[] sorted = new long[] {10, 20, 30, 40, 50};
    // (n-1)*p = 4 * 0.99 = 3.96 → 40 + 0.96 * 10 = 49.6
    assertThat(AdminRequestMetricsService.percentile(sorted, 0.99)).isCloseTo(49.6, within(0.0001));
  }

  @Test
  void windowParsingNormalizesCommonAliases() {
    assertThat(AdminRequestMetricsService.Window.parse("1h"))
        .isEqualTo(AdminRequestMetricsService.Window.H1);
    assertThat(AdminRequestMetricsService.Window.parse("24h"))
        .isEqualTo(AdminRequestMetricsService.Window.H24);
    assertThat(AdminRequestMetricsService.Window.parse("1d"))
        .isEqualTo(AdminRequestMetricsService.Window.H24);
    assertThat(AdminRequestMetricsService.Window.parse("7d"))
        .isEqualTo(AdminRequestMetricsService.Window.D7);
    assertThat(AdminRequestMetricsService.Window.parse("week"))
        .isEqualTo(AdminRequestMetricsService.Window.D7);
    assertThat(AdminRequestMetricsService.Window.parse(null))
        .isEqualTo(AdminRequestMetricsService.Window.H1);
    assertThat(AdminRequestMetricsService.Window.parse("unknown"))
        .isEqualTo(AdminRequestMetricsService.Window.H1);
  }
}
