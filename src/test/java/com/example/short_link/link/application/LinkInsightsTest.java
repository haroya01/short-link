package com.example.short_link.link.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class LinkInsightsTest {

  private final LinkInsights insights = new LinkInsights();

  @Test
  void returnsEmptyWhenTotalBelowThreshold() {
    List<LinkStats.Insight> result =
        insights.compute(5, 0, List.of(), List.of(), List.of(), null, null, List.of());
    assertThat(result).isEmpty();
  }

  @Test
  void detectsBotRatioWarning() {
    List<LinkStats.Insight> result =
        insights.compute(100, 50, List.of(), List.of(), List.of(), null, null, List.of());
    assertThat(result).extracting(LinkStats.Insight::type).contains("BOT_RATIO_HIGH");
    LinkStats.Insight bot =
        result.stream().filter(i -> i.type().equals("BOT_RATIO_HIGH")).findFirst().orElseThrow();
    assertThat(bot.severity()).isEqualTo("warning");
  }

  @Test
  void detectsTopChannelWhenAboveThreshold() {
    var channels =
        List.of(
            new LinkStats.ChannelClick("social", 80L), new LinkStats.ChannelClick("direct", 20L));
    List<LinkStats.Insight> result =
        insights.compute(100, 0, List.of(), channels, List.of(), null, null, List.of());
    assertThat(result).extracting(LinkStats.Insight::type).contains("TOP_CHANNEL");
  }

  @Test
  void detectsCountryConcentration() {
    var countries =
        List.of(new LinkStats.CountryClick("KR", 90L), new LinkStats.CountryClick("US", 10L));
    List<LinkStats.Insight> result =
        insights.compute(100, 0, List.of(), List.of(), countries, null, null, List.of());
    assertThat(result).extracting(LinkStats.Insight::type).contains("COUNTRY_CONCENTRATION");
  }

  @Test
  void detectsPeakHour() {
    var heatmap =
        List.of(
            new LinkStats.HeatmapCell("MONDAY", 10, 5L),
            new LinkStats.HeatmapCell("TUESDAY", 21, 30L));
    List<LinkStats.Insight> result =
        insights.compute(100, 0, heatmap, List.of(), List.of(), null, null, List.of());
    var peak = result.stream().filter(i -> i.type().equals("PEAK_HOUR")).findFirst().orElseThrow();
    assertThat(peak.data()).containsEntry("dayOfWeek", "TUESDAY").containsEntry("hour", 21);
  }

  @Test
  void detectsFastDecay() {
    var lifecycle =
        new LinkStats.Lifecycle(
            List.of(new LinkStats.DayClick(0, 90L), new LinkStats.DayClick(1, 10L)), 0);
    List<LinkStats.Insight> result =
        insights.compute(100, 0, List.of(), List.of(), List.of(), null, lifecycle, List.of());
    assertThat(result).extracting(LinkStats.Insight::type).contains("FAST_DECAY");
  }

  @Test
  void detectsWeekOverWeekGrowth() {
    LocalDate base = LocalDate.of(2026, 4, 1);
    var daily = new java.util.ArrayList<LinkStats.DailyClick>();
    for (int i = 0; i < 14; i++) {
      daily.add(new LinkStats.DailyClick(base.plusDays(i), i < 7 ? 5L : 10L));
    }
    List<LinkStats.Insight> result =
        insights.compute(100, 0, List.of(), List.of(), List.of(), null, null, daily);
    assertThat(result).extracting(LinkStats.Insight::type).contains("WEEK_OVER_WEEK");
  }

  @Test
  void weekOverWeekDeclineEmitsWarning() {
    LocalDate base = LocalDate.of(2026, 4, 1);
    var daily = new java.util.ArrayList<LinkStats.DailyClick>();
    for (int i = 0; i < 14; i++) {
      daily.add(new LinkStats.DailyClick(base.plusDays(i), i < 7 ? 20L : 5L));
    }
    List<LinkStats.Insight> result =
        insights.compute(200, 0, List.of(), List.of(), List.of(), null, null, daily);
    var wow =
        result.stream().filter(i -> i.type().equals("WEEK_OVER_WEEK")).findFirst().orElseThrow();
    assertThat(wow.severity()).isEqualTo("warning");
  }

  @Test
  void weekOverWeekSkippedWhenPrevWeekTooSmall() {
    LocalDate base = LocalDate.of(2026, 4, 1);
    var daily = new java.util.ArrayList<LinkStats.DailyClick>();
    for (int i = 0; i < 14; i++) daily.add(new LinkStats.DailyClick(base.plusDays(i), 0L));
    daily.set(13, new LinkStats.DailyClick(base.plusDays(13), 50L));
    List<LinkStats.Insight> result =
        insights.compute(100, 0, List.of(), List.of(), List.of(), null, null, daily);
    assertThat(result).extracting(LinkStats.Insight::type).doesNotContain("WEEK_OVER_WEEK");
  }

  @Test
  void detectsVisitorMix() {
    var rr = new LinkStats.ReturnRate(7, 3, 0.3);
    List<LinkStats.Insight> result =
        insights.compute(100, 0, List.of(), List.of(), List.of(), rr, null, List.of());
    var mix = result.stream().filter(i -> i.type().equals("VISITOR_MIX")).findFirst().orElseThrow();
    assertThat(mix.data()).containsKey("newShare").containsKey("returningShare");
  }

  @Test
  void visitorMixSkippedWhenTooFewVisitors() {
    var rr = new LinkStats.ReturnRate(2, 1, 0.33);
    List<LinkStats.Insight> result =
        insights.compute(100, 0, List.of(), List.of(), List.of(), rr, null, List.of());
    assertThat(result).extracting(LinkStats.Insight::type).doesNotContain("VISITOR_MIX");
  }

  @Test
  void peakHourSkippedWhenAllCellsTooLow() {
    var heatmap =
        List.of(
            new LinkStats.HeatmapCell("MONDAY", 10, 1L),
            new LinkStats.HeatmapCell("TUESDAY", 21, 1L));
    List<LinkStats.Insight> result =
        insights.compute(100, 0, heatmap, List.of(), List.of(), null, null, List.of());
    assertThat(result).extracting(LinkStats.Insight::type).doesNotContain("PEAK_HOUR");
  }

  @Test
  void fastDecaySkippedWhenFirstWeekHasNoClicks() {
    var lifecycle = new LinkStats.Lifecycle(List.of(new LinkStats.DayClick(10, 50L)), null);
    List<LinkStats.Insight> result =
        insights.compute(100, 0, List.of(), List.of(), List.of(), null, lifecycle, List.of());
    assertThat(result).extracting(LinkStats.Insight::type).doesNotContain("FAST_DECAY");
  }

  @Test
  void unknownDayOfWeekFallsBackInPeakHour() {
    var heatmap = List.of(new LinkStats.HeatmapCell("UNKNOWN", 5, 30L));
    List<LinkStats.Insight> result =
        insights.compute(100, 0, heatmap, List.of(), List.of(), null, null, List.of());
    var peak = result.stream().filter(i -> i.type().equals("PEAK_HOUR")).findFirst().orElseThrow();
    assertThat(peak.message()).contains("UNKNOWN");
  }

  @Test
  void topChannelSkippedWhenBelowThreshold() {
    var channels =
        List.of(
            new LinkStats.ChannelClick("social", 30L),
            new LinkStats.ChannelClick("direct", 30L),
            new LinkStats.ChannelClick("search", 40L));
    List<LinkStats.Insight> result =
        insights.compute(100, 0, List.of(), channels, List.of(), null, null, List.of());
    assertThat(result).extracting(LinkStats.Insight::type).doesNotContain("TOP_CHANNEL");
  }

  @Test
  void countryConcentrationSkippedWhenBelowThreshold() {
    var countries =
        List.of(new LinkStats.CountryClick("KR", 50L), new LinkStats.CountryClick("US", 50L));
    List<LinkStats.Insight> result =
        insights.compute(100, 0, List.of(), List.of(), countries, null, null, List.of());
    assertThat(result).extracting(LinkStats.Insight::type).doesNotContain("COUNTRY_CONCENTRATION");
  }
}
