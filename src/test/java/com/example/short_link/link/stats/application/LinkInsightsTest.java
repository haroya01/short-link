package com.example.short_link.link.stats.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.link.application.dto.LinkStats;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.HostFirstSeenRow;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;

class LinkInsightsTest {

  private static final LocalDate REPORT_DATE = LocalDate.of(2026, 9, 12);

  private static MessageSource messageSource() {
    var ms = new ResourceBundleMessageSource();
    ms.setBasename("messages");
    ms.setDefaultEncoding("UTF-8");
    ms.setFallbackToSystemLocale(false);
    return ms;
  }

  private final LinkInsights insights = new LinkInsights(messageSource());

  private static HostFirstSeenRow firstSeen(String host, Long epoch) {
    return new HostFirstSeenRow() {
      public String getHost() {
        return host;
      }

      public Long getFirstSeenEpoch() {
        return epoch;
      }
    };
  }

  private static LinkInsights.ReportFacts.ReportFactsBuilder generalFacts(long total) {
    return LinkInsights.ReportFacts.builder()
        .reportDate(REPORT_DATE)
        .total(total)
        .heatmap(List.of())
        .channels(List.of())
        .countries(List.of())
        .dailyClicks(List.of());
  }

  private static LinkInsights.ReportFacts facts(long total, long human) {
    return LinkInsights.ReportFacts.builder()
        .reportDate(REPORT_DATE)
        .total(total)
        .human(human)
        .heatmap(List.of())
        .channels(List.of())
        .countries(List.of())
        .dailyClicks(List.of())
        .clientApps(List.of(new LinkStats.ClientAppClick("kakaotalk", 3)))
        .channelDepth(List.of(new LinkStats.ChannelDepth("loyal.example", 8, null, 5, 0.5)))
        .build();
  }

  @Test
  void reportBelowGeneralSampleStillEvaluatesLoyaltyWithoutReadingChannelHistory() {
    List<LinkStats.Insight> report =
        insights.computeReport(
            facts(9, 9),
            () -> {
              throw new AssertionError("small samples must not query channel history");
            });

    assertThat(report).extracting(LinkStats.Insight::type).containsExactly("CHANNEL_LOYALTY");
  }

  @Test
  void reportKeepsChannelJumpThenInAppThenLoyaltyOrder() {
    List<LinkStats.Insight> report =
        insights.computeReport(
            facts(10, 10),
            () ->
                List.of(
                    firstSeen("origin.example", 0L),
                    firstSeen("early.example", 3599L),
                    firstSeen("jump.example", 3600L)));

    assertThat(report)
        .extracting(LinkStats.Insight::type)
        .containsExactly("CHANNEL_JUMP", "IN_APP_BROWSER", "CHANNEL_LOYALTY");
    assertThat(report.getFirst().data())
        .containsEntry("jumpedTo", "jump.example")
        .containsEntry("gapHours", 1L);
  }

  @Test
  void channelJumpKeepsAnUnknownOriginFromProducingAnInsight() {
    assertThat(insights.channelJump(List.of(firstSeen(null, 0L), firstSeen("later", 7200L))))
        .isEmpty();
  }

  @AfterEach
  void resetLocale() {
    LocaleContextHolder.resetLocaleContext();
  }

  @Test
  void messageLocalizesByRequestLocale() {
    var heatmap = List.of(new LinkStats.HeatmapCell("TUESDAY", 21, 30L));

    LocaleContextHolder.setLocale(Locale.ENGLISH);
    var en =
        insights.computeReport(generalFacts(100).heatmap(heatmap).build(), List::of).stream()
            .filter(i -> i.type().equals("PEAK_HOUR"))
            .findFirst()
            .orElseThrow();
    assertThat(en.message()).contains("Peak time").contains("Tuesday");

    LocaleContextHolder.setLocale(Locale.KOREAN);
    var ko =
        insights.computeReport(generalFacts(100).heatmap(heatmap).build(), List::of).stream()
            .filter(i -> i.type().equals("PEAK_HOUR"))
            .findFirst()
            .orElseThrow();
    assertThat(ko.message()).contains("피크 시간").contains("화요일");

    LocaleContextHolder.setLocale(Locale.JAPANESE);
    var ja =
        insights.computeReport(generalFacts(100).heatmap(heatmap).build(), List::of).stream()
            .filter(i -> i.type().equals("PEAK_HOUR"))
            .findFirst()
            .orElseThrow();
    assertThat(ja.message()).contains("ピーク時間").contains("火曜日");
  }

  @Test
  void returnsEmptyWhenTotalBelowThreshold() {
    List<LinkStats.Insight> result = insights.computeReport(generalFacts(5).build(), List::of);
    assertThat(result).isEmpty();
  }

  @Test
  void detectsBotRatioWarning() {
    List<LinkStats.Insight> result =
        insights.computeReport(generalFacts(100).bot(50).build(), List::of);
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
        insights.computeReport(generalFacts(100).channels(channels).build(), List::of);
    assertThat(result).extracting(LinkStats.Insight::type).contains("TOP_CHANNEL");
  }

  @Test
  void detectsCountryConcentration() {
    var countries =
        List.of(new LinkStats.CountryClick("KR", 90L), new LinkStats.CountryClick("US", 10L));
    List<LinkStats.Insight> result =
        insights.computeReport(generalFacts(100).countries(countries).build(), List::of);
    assertThat(result).extracting(LinkStats.Insight::type).contains("COUNTRY_CONCENTRATION");
  }

  @Test
  void detectsPeakHour() {
    var heatmap =
        List.of(
            new LinkStats.HeatmapCell("MONDAY", 10, 5L),
            new LinkStats.HeatmapCell("TUESDAY", 21, 30L));
    List<LinkStats.Insight> result =
        insights.computeReport(generalFacts(100).heatmap(heatmap).build(), List::of);
    var peak = result.stream().filter(i -> i.type().equals("PEAK_HOUR")).findFirst().orElseThrow();
    assertThat(peak.data()).containsEntry("dayOfWeek", "TUESDAY").containsEntry("hour", 21);
  }

  @Test
  void detectsFastDecay() {
    var lifecycle =
        new LinkStats.Lifecycle(
            List.of(new LinkStats.DayClick(0, 90L), new LinkStats.DayClick(1, 10L)), 0);
    List<LinkStats.Insight> result =
        insights.computeReport(generalFacts(100).lifecycle(lifecycle).build(), List::of);
    assertThat(result).extracting(LinkStats.Insight::type).contains("FAST_DECAY");
  }

  @Test
  void detectsWeekOverWeekGrowth() {
    LocalDate base = LocalDate.of(2026, 4, 1);
    var daily = new ArrayList<LinkStats.DailyClick>();
    for (int i = 0; i < 14; i++) {
      daily.add(new LinkStats.DailyClick(base.plusDays(i), i < 7 ? 5L : 10L));
    }
    List<LinkStats.Insight> result =
        insights.computeReport(generalFacts(100).dailyClicks(daily).build(), List::of);
    assertThat(result).extracting(LinkStats.Insight::type).contains("WEEK_OVER_WEEK");
  }

  @Test
  void weekOverWeekDeclineEmitsWarning() {
    LocalDate base = LocalDate.of(2026, 4, 1);
    var daily = new ArrayList<LinkStats.DailyClick>();
    for (int i = 0; i < 14; i++) {
      daily.add(new LinkStats.DailyClick(base.plusDays(i), i < 7 ? 20L : 5L));
    }
    List<LinkStats.Insight> result =
        insights.computeReport(generalFacts(200).dailyClicks(daily).build(), List::of);
    var wow =
        result.stream().filter(i -> i.type().equals("WEEK_OVER_WEEK")).findFirst().orElseThrow();
    assertThat(wow.severity()).isEqualTo("warning");
  }

  @Test
  void weekOverWeekSkippedWhenPrevWeekTooSmall() {
    LocalDate base = LocalDate.of(2026, 4, 1);
    var daily = new ArrayList<LinkStats.DailyClick>();
    for (int i = 0; i < 14; i++) daily.add(new LinkStats.DailyClick(base.plusDays(i), 0L));
    daily.set(13, new LinkStats.DailyClick(base.plusDays(13), 50L));
    List<LinkStats.Insight> result =
        insights.computeReport(generalFacts(100).dailyClicks(daily).build(), List::of);
    assertThat(result).extracting(LinkStats.Insight::type).doesNotContain("WEEK_OVER_WEEK");
  }

  @Test
  void detectsVisitorMix() {
    var rr = new LinkStats.ReturnRate(7, 3, 0.3);
    List<LinkStats.Insight> result =
        insights.computeReport(generalFacts(100).returnRate(rr).build(), List::of);
    var mix = result.stream().filter(i -> i.type().equals("VISITOR_MIX")).findFirst().orElseThrow();
    assertThat(mix.data()).containsKey("newShare").containsKey("returningShare");
  }

  @Test
  void visitorMixSkippedWhenTooFewVisitors() {
    var rr = new LinkStats.ReturnRate(2, 1, 0.33);
    List<LinkStats.Insight> result =
        insights.computeReport(generalFacts(100).returnRate(rr).build(), List::of);
    assertThat(result).extracting(LinkStats.Insight::type).doesNotContain("VISITOR_MIX");
  }

  @Test
  void peakHourSkippedWhenAllCellsTooLow() {
    var heatmap =
        List.of(
            new LinkStats.HeatmapCell("MONDAY", 10, 1L),
            new LinkStats.HeatmapCell("TUESDAY", 21, 1L));
    List<LinkStats.Insight> result =
        insights.computeReport(generalFacts(100).heatmap(heatmap).build(), List::of);
    assertThat(result).extracting(LinkStats.Insight::type).doesNotContain("PEAK_HOUR");
  }

  @Test
  void fastDecaySkippedWhenFirstWeekHasNoClicks() {
    var lifecycle = new LinkStats.Lifecycle(List.of(new LinkStats.DayClick(10, 50L)), null);
    List<LinkStats.Insight> result =
        insights.computeReport(generalFacts(100).lifecycle(lifecycle).build(), List::of);
    assertThat(result).extracting(LinkStats.Insight::type).doesNotContain("FAST_DECAY");
  }

  @Test
  void unknownDayOfWeekFallsBackInPeakHour() {
    var heatmap = List.of(new LinkStats.HeatmapCell("UNKNOWN", 5, 30L));
    List<LinkStats.Insight> result =
        insights.computeReport(generalFacts(100).heatmap(heatmap).build(), List::of);
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
        insights.computeReport(generalFacts(100).channels(channels).build(), List::of);
    assertThat(result).extracting(LinkStats.Insight::type).doesNotContain("TOP_CHANNEL");
  }

  @Test
  void countryConcentrationSkippedWhenBelowThreshold() {
    var countries =
        List.of(new LinkStats.CountryClick("KR", 50L), new LinkStats.CountryClick("US", 50L));
    List<LinkStats.Insight> result =
        insights.computeReport(generalFacts(100).countries(countries).build(), List::of);
    assertThat(result).extracting(LinkStats.Insight::type).doesNotContain("COUNTRY_CONCENTRATION");
  }

  @Test
  void peakHourCoversAllKoreanWeekdayLabels() {
    for (String dow : List.of("WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY")) {
      var heatmap = List.of(new LinkStats.HeatmapCell(dow, 20, 30L));
      List<LinkStats.Insight> result =
          insights.computeReport(generalFacts(100).heatmap(heatmap).build(), List::of);
      var peak =
          result.stream().filter(i -> i.type().equals("PEAK_HOUR")).findFirst().orElseThrow();
      assertThat(peak.data()).containsEntry("dayOfWeek", dow);
    }
  }

  @Test
  void detectsDarkSocialWhenDirectShareHigh() {
    var channels =
        List.of(
            new LinkStats.ChannelClick("direct", 40L), new LinkStats.ChannelClick("social", 60L));
    List<LinkStats.Insight> result =
        insights.computeReport(generalFacts(100).channels(channels).build(), List::of);
    var ds = result.stream().filter(i -> i.type().equals("DARK_SOCIAL")).findFirst().orElseThrow();
    assertThat(ds.severity()).isEqualTo("info");
    assertThat(ds.data()).containsKey("share").containsEntry("directClicks", 40L);
  }

  @Test
  void darkSocialSkippedWhenDirectShareLow() {
    var channels =
        List.of(
            new LinkStats.ChannelClick("direct", 20L), new LinkStats.ChannelClick("social", 80L));
    List<LinkStats.Insight> result =
        insights.computeReport(generalFacts(100).channels(channels).build(), List::of);
    assertThat(result).extracting(LinkStats.Insight::type).doesNotContain("DARK_SOCIAL");
  }

  @Test
  void detectsSecondWindAfterDormancy() {
    LocalDate base = LocalDate.of(2026, 4, 1);
    var daily = new ArrayList<LinkStats.DailyClick>();
    for (int i = 0; i < 7; i++) daily.add(new LinkStats.DailyClick(base.plusDays(i), 0L)); // 잠잠
    for (int i = 7; i < 10; i++) daily.add(new LinkStats.DailyClick(base.plusDays(i), 2L)); // 최근 부활
    List<LinkStats.Insight> result =
        insights.computeReport(generalFacts(100).dailyClicks(daily).build(), List::of);
    var sw = result.stream().filter(i -> i.type().equals("SECOND_WIND")).findFirst().orElseThrow();
    assertThat(sw.severity()).isEqualTo("info");
  }

  @Test
  void secondWindSkippedWhenSteady() {
    LocalDate base = LocalDate.of(2026, 4, 1);
    var daily = new ArrayList<LinkStats.DailyClick>();
    for (int i = 0; i < 10; i++) daily.add(new LinkStats.DailyClick(base.plusDays(i), 5L)); // 꾸준
    List<LinkStats.Insight> result =
        insights.computeReport(generalFacts(100).dailyClicks(daily).build(), List::of);
    assertThat(result).extracting(LinkStats.Insight::type).doesNotContain("SECOND_WIND");
  }

  @Test
  void detectsDormancyWhenLongIdle() {
    var daily = List.of(new LinkStats.DailyClick(REPORT_DATE.minusDays(12), 5L));
    List<LinkStats.Insight> result =
        insights.computeReport(generalFacts(100).dailyClicks(daily).build(), List::of);
    var dm = result.stream().filter(i -> i.type().equals("DORMANT")).findFirst().orElseThrow();
    assertThat(dm.severity()).isEqualTo("warning");
    assertThat(dm.data()).containsKey("daysIdle");
  }

  @Test
  void dormancyUsesTheReportsLocalDateAtTheSevenDayBoundary() {
    var daily = List.of(new LinkStats.DailyClick(REPORT_DATE.minusDays(6), 5L));

    var beforeMidnight =
        insights.computeReport(generalFacts(100).dailyClicks(daily).build(), List::of);
    var afterMidnight =
        insights.computeReport(
            generalFacts(100).dailyClicks(daily).reportDate(REPORT_DATE.plusDays(1)).build(),
            List::of);

    assertThat(beforeMidnight).extracting(LinkStats.Insight::type).doesNotContain("DORMANT");
    assertThat(afterMidnight)
        .filteredOn(insight -> insight.type().equals("DORMANT"))
        .singleElement()
        .satisfies(insight -> assertThat(insight.data()).containsEntry("daysIdle", 7L));
  }

  @Test
  void dormancySkippedWhenRecentlyActive() {
    var daily = List.of(new LinkStats.DailyClick(REPORT_DATE.minusDays(1), 5L));
    List<LinkStats.Insight> result =
        insights.computeReport(generalFacts(100).dailyClicks(daily).build(), List::of);
    assertThat(result).extracting(LinkStats.Insight::type).doesNotContain("DORMANT");
  }

  @Test
  void dormancySkippedWhenNeverActive() {
    var daily = List.of(new LinkStats.DailyClick(REPORT_DATE.minusDays(12), 0L));
    List<LinkStats.Insight> result =
        insights.computeReport(generalFacts(100).dailyClicks(daily).build(), List::of);
    assertThat(result).extracting(LinkStats.Insight::type).doesNotContain("DORMANT");
  }

  @Test
  void inAppBrowser_firesWhenTheShareIsBigEnough() {
    LocaleContextHolder.setLocale(Locale.KOREAN);
    var apps =
        List.of(
            new LinkStats.ClientAppClick("kakaotalk", 25L),
            new LinkStats.ClientAppClick("instagram", 10L));

    var insight = insights.inAppBrowser(apps, 100).orElseThrow();

    assertThat(insight.type()).isEqualTo("IN_APP_BROWSER");
    assertThat(insight.severity()).isEqualTo("info");
    assertThat(insight.message()).contains("카카오톡").contains("35.0");
    assertThat(insight.data())
        .containsEntry("topApp", "kakaotalk")
        .containsEntry("inAppClicks", 35L)
        .containsEntry("share", 0.35);
  }

  @Test
  void inAppBrowser_localizesTheAppName() {
    var apps = List.of(new LinkStats.ClientAppClick("kakaotalk", 50L));

    LocaleContextHolder.setLocale(Locale.ENGLISH);
    assertThat(insights.inAppBrowser(apps, 100).orElseThrow().message()).contains("KakaoTalk");

    LocaleContextHolder.setLocale(Locale.JAPANESE);
    assertThat(insights.inAppBrowser(apps, 100).orElseThrow().message()).contains("カカオトーク");
  }

  @Test
  void inAppBrowser_fallsBackToTheRawAppName() {
    LocaleContextHolder.setLocale(Locale.KOREAN);
    var apps = List.of(new LinkStats.ClientAppClick("someNewApp", 50L));

    assertThat(insights.inAppBrowser(apps, 100).orElseThrow().message()).contains("someNewApp");
  }

  @Test
  void inAppBrowser_silentWhenShareIsSmall() {
    var apps = List.of(new LinkStats.ClientAppClick("kakaotalk", 5L));

    assertThat(insights.inAppBrowser(apps, 100)).isEmpty();
  }

  @Test
  void inAppBrowser_silentWithoutEnoughClicksOrData() {
    assertThat(insights.inAppBrowser(List.of(new LinkStats.ClientAppClick("kakaotalk", 5L)), 5))
        .isEmpty();
    assertThat(insights.inAppBrowser(List.of(), 100)).isEmpty();
    assertThat(insights.inAppBrowser(null, 100)).isEmpty();
  }

  private static LinkStats.ChannelDepth depth(String host, long returning, double ratio) {
    return new LinkStats.ChannelDepth(host, 100L, java.time.Instant.now(), returning, ratio);
  }

  @Test
  void channelLoyalty_picksTheHighestReturnRateNotTheBiggestChannel() {
    LocaleContextHolder.setLocale(Locale.KOREAN);
    var channels = List.of(depth("instagram.com", 6L, 0.35), depth("notion.so", 8L, 0.62));

    var insight = insights.channelLoyalty(channels).orElseThrow();

    assertThat(insight.type()).isEqualTo("CHANNEL_LOYALTY");
    assertThat(insight.severity()).isEqualTo("info");
    assertThat(insight.message()).contains("notion.so").contains("62.0");
    assertThat(insight.data())
        .containsEntry("host", "notion.so")
        .containsEntry("returnRatio", 0.62)
        .containsEntry("returningVisitors", 8L);
  }

  @Test
  void channelLoyalty_silentWhenTheRateIsOrdinary() {
    assertThat(insights.channelLoyalty(List.of(depth("instagram.com", 9L, 0.12)))).isEmpty();
  }

  @Test
  void channelLoyalty_silentWhenTheSampleIsTiny() {
    assertThat(insights.channelLoyalty(List.of(depth("instagram.com", 2L, 1.0)))).isEmpty();
  }

  @Test
  void channelLoyalty_silentWithoutChannels() {
    assertThat(insights.channelLoyalty(List.of())).isEmpty();
    assertThat(insights.channelLoyalty(null)).isEmpty();
  }
}
