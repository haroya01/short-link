package com.example.short_link.link.stats.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.link.application.dto.LinkStats;
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

  private static MessageSource messageSource() {
    var ms = new ResourceBundleMessageSource();
    ms.setBasename("messages");
    ms.setDefaultEncoding("UTF-8");
    ms.setFallbackToSystemLocale(false);
    return ms;
  }

  private final LinkInsights insights = new LinkInsights(messageSource());

  @AfterEach
  void resetLocale() {
    LocaleContextHolder.resetLocaleContext();
  }

  @Test
  void messageLocalizesByRequestLocale() {
    var heatmap = List.of(new LinkStats.HeatmapCell("TUESDAY", 21, 30L));

    LocaleContextHolder.setLocale(Locale.ENGLISH);
    var en =
        insights.compute(100, 0, heatmap, List.of(), List.of(), null, null, List.of()).stream()
            .filter(i -> i.type().equals("PEAK_HOUR"))
            .findFirst()
            .orElseThrow();
    assertThat(en.message()).contains("Peak time").contains("Tuesday");

    LocaleContextHolder.setLocale(Locale.KOREAN);
    var ko =
        insights.compute(100, 0, heatmap, List.of(), List.of(), null, null, List.of()).stream()
            .filter(i -> i.type().equals("PEAK_HOUR"))
            .findFirst()
            .orElseThrow();
    assertThat(ko.message()).contains("피크 시간").contains("화요일");

    LocaleContextHolder.setLocale(Locale.JAPANESE);
    var ja =
        insights.compute(100, 0, heatmap, List.of(), List.of(), null, null, List.of()).stream()
            .filter(i -> i.type().equals("PEAK_HOUR"))
            .findFirst()
            .orElseThrow();
    assertThat(ja.message()).contains("ピーク時間").contains("火曜日");
  }

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
    var daily = new ArrayList<LinkStats.DailyClick>();
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
    var daily = new ArrayList<LinkStats.DailyClick>();
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
    var daily = new ArrayList<LinkStats.DailyClick>();
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

  @Test
  void peakHourCoversAllKoreanWeekdayLabels() {
    // dayOfWeekKo 의 요일별 갈래 — 수~일까지 라벨이 모두 채워진다(피크 메시지에 한글 요일).
    for (String dow : List.of("WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY")) {
      var heatmap = List.of(new LinkStats.HeatmapCell(dow, 20, 30L));
      List<LinkStats.Insight> result =
          insights.compute(100, 0, heatmap, List.of(), List.of(), null, null, List.of());
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
        insights.compute(100, 0, List.of(), channels, List.of(), null, null, List.of());
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
        insights.compute(100, 0, List.of(), channels, List.of(), null, null, List.of());
    assertThat(result).extracting(LinkStats.Insight::type).doesNotContain("DARK_SOCIAL");
  }

  @Test
  void detectsSecondWindAfterDormancy() {
    LocalDate base = LocalDate.of(2026, 4, 1);
    var daily = new ArrayList<LinkStats.DailyClick>();
    for (int i = 0; i < 7; i++) daily.add(new LinkStats.DailyClick(base.plusDays(i), 0L)); // 잠잠
    for (int i = 7; i < 10; i++) daily.add(new LinkStats.DailyClick(base.plusDays(i), 2L)); // 최근 부활
    List<LinkStats.Insight> result =
        insights.compute(100, 0, List.of(), List.of(), List.of(), null, null, daily);
    var sw = result.stream().filter(i -> i.type().equals("SECOND_WIND")).findFirst().orElseThrow();
    assertThat(sw.severity()).isEqualTo("info");
  }

  @Test
  void secondWindSkippedWhenSteady() {
    LocalDate base = LocalDate.of(2026, 4, 1);
    var daily = new ArrayList<LinkStats.DailyClick>();
    for (int i = 0; i < 10; i++) daily.add(new LinkStats.DailyClick(base.plusDays(i), 5L)); // 꾸준
    List<LinkStats.Insight> result =
        insights.compute(100, 0, List.of(), List.of(), List.of(), null, null, daily);
    assertThat(result).extracting(LinkStats.Insight::type).doesNotContain("SECOND_WIND");
  }

  @Test
  void detectsDormancyWhenLongIdle() {
    var daily = List.of(new LinkStats.DailyClick(LocalDate.now().minusDays(12), 5L));
    List<LinkStats.Insight> result =
        insights.compute(100, 0, List.of(), List.of(), List.of(), null, null, daily);
    var dm = result.stream().filter(i -> i.type().equals("DORMANT")).findFirst().orElseThrow();
    assertThat(dm.severity()).isEqualTo("warning");
    assertThat(dm.data()).containsKey("daysIdle");
  }

  @Test
  void dormancySkippedWhenRecentlyActive() {
    var daily = List.of(new LinkStats.DailyClick(LocalDate.now().minusDays(1), 5L));
    List<LinkStats.Insight> result =
        insights.compute(100, 0, List.of(), List.of(), List.of(), null, null, daily);
    assertThat(result).extracting(LinkStats.Insight::type).doesNotContain("DORMANT");
  }

  @Test
  void dormancySkippedWhenNeverActive() {
    var daily = List.of(new LinkStats.DailyClick(LocalDate.now().minusDays(12), 0L));
    List<LinkStats.Insight> result =
        insights.compute(100, 0, List.of(), List.of(), List.of(), null, null, daily);
    assertThat(result).extracting(LinkStats.Insight::type).doesNotContain("DORMANT");
  }

  // ─── 인앱 브라우저 비중 ───

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

  /** 이름 없는 앱은 저장된 값 그대로 — 요일 폴백과 같은 방식이라 새 앱이 붙어도 예외가 안 난다. */
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

  // ─── 채널 충성도 ───

  private static LinkStats.ChannelDepth depth(String host, long returning, double ratio) {
    return new LinkStats.ChannelDepth(host, 100L, java.time.Instant.now(), returning, ratio);
  }

  @Test
  void channelLoyalty_picksTheHighestReturnRateNotTheBiggestChannel() {
    LocaleContextHolder.setLocale(Locale.KOREAN);
    var channels =
        List.of(
            depth("instagram.com", 6L, 0.35),
            // 클릭은 적어도 재방문율이 가장 높은 채널이 이야기의 주인공이다.
            depth("notion.so", 8L, 0.62));

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

  /** 재방문 2명으로 100%를 만들어도 신호가 아니라 잡음이다. */
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
