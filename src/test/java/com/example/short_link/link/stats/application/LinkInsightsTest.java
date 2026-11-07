package com.example.short_link.link.stats.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.link.application.dto.LinkStats;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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

  private static LinkInsights.ReportFacts.ReportFactsBuilder stats() {
    return LinkInsights.ReportFacts.builder()
        .total(100)
        .human(90)
        .bot(10)
        .velocity(new LinkStats.Velocity(0, 0, 0))
        .today(LocalDate.parse("2026-07-15"));
  }

  private static LinkInsights.ReportFacts.ReportFactsBuilder realLinkStats() {
    return LinkInsights.ReportFacts.builder()
        .total(1309)
        .human(1125)
        .bot(184)
        .velocity(new LinkStats.Velocity(0, 0, 0))
        .returnRate(new LinkStats.ReturnRate(300, 60, 60.0 / 360))
        .referrerHosts(
            List.of(
                new LinkStats.ReferrerHostClick("instagram.com", 383),
                new LinkStats.ReferrerHostClick("pf.kakao.com", 236),
                new LinkStats.ReferrerHostClick("haru.notion.site", 146),
                new LinkStats.ReferrerHostClick("x.com", 101),
                new LinkStats.ReferrerHostClick("blog.naver.com", 68)))
        .devices(
            List.of(
                new LinkStats.DeviceClick("mobile", 877),
                new LinkStats.DeviceClick("desktop", 203),
                new LinkStats.DeviceClick("tablet", 45)))
        .clientApps(
            List.of(
                new LinkStats.ClientAppClick("kakaotalk", 191),
                new LinkStats.ClientAppClick("instagram", 101),
                new LinkStats.ClientAppClick("naver", 34),
                new LinkStats.ClientAppClick("line", 23)))
        .countries(
            List.of(
                new LinkStats.CountryClick("KR", 810),
                new LinkStats.CountryClick("JP", 214),
                new LinkStats.CountryClick("unknown", 101)))
        .rawRetentionDays(90)
        .rawSince(Instant.parse("2026-08-04T00:00:00Z"))
        .today(LocalDate.parse("2026-10-15"));
  }

  private List<LinkStats.Insight> journal(LinkInsights.ReportFacts.ReportFactsBuilder facts) {
    return insights.computeReport(facts.build());
  }

  private Optional<LinkStats.Insight> entry(
      String code, LinkInsights.ReportFacts.ReportFactsBuilder facts) {
    return journal(facts).stream().filter(i -> i.code().equals(code)).findFirst();
  }

  private static List<LinkStats.DailyClick> days(String month, long... counts) {
    return IntStream.range(0, counts.length)
        .mapToObj(
            i ->
                new LinkStats.DailyClick(
                    LocalDate.parse("2026-" + month + "-01").plusDays(i), counts[i]))
        .toList();
  }

  private static LinkStats.ChannelDepth depth(String host, long count, long returning, double r) {
    return new LinkStats.ChannelDepth(host, count, null, returning, r);
  }

  @Test
  void noClicksMeansNoSentences() {
    assertThat(journal(stats().total(0))).isEmpty();
  }

  @Test
  void rulesBelowTheirGuardStaySilent() {
    List<LinkStats.Insight> entries =
        journal(
            stats()
                .bot(2)
                .human(98)
                .referrerHosts(
                    List.of(
                        new LinkStats.ReferrerHostClick("a.com", 10),
                        new LinkStats.ReferrerHostClick("b.com", 9),
                        new LinkStats.ReferrerHostClick("c.com", 8),
                        new LinkStats.ReferrerHostClick("d.com", 8),
                        new LinkStats.ReferrerHostClick("e.com", 8))));

    assertThat(entries).extracting(LinkStats.Insight::code).doesNotContain("topSource");
    assertThat(entries).noneMatch(i -> i.code().startsWith("bot"));
  }

  @Test
  void trendNeedsThePreviousSevenDays() {
    long[] flat = IntStream.range(0, 14).mapToLong(i -> i < 7 ? 0 : 10).toArray();

    assertThat(entry("trendUp", stats().dailyClicks(days("07", flat)))).isEmpty();
  }

  @Test
  void trendCountsCalendarDaysWhenRecentDaysHadNoClicks() {
    long[] busyThenQuiet = IntStream.range(0, 20).mapToLong(i -> 20).toArray();

    LinkStats.Insight down =
        entry(
                "trendDown",
                stats().dailyClicks(days("07", busyThenQuiet)).today(LocalDate.parse("2026-07-31")))
            .orElseThrow();

    assertThat(down.params()).isEqualTo(Map.of("percent", 100L, "count", 0L));
    assertThat(down.spark())
        .containsExactly(20L, 20L, 20L, 20L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);
  }

  @Test
  void trendLeavesOutTheDayStillInProgress() {
    long[] steadyThenTodaySoFar =
        IntStream.range(0, 15).mapToLong(i -> i < 8 ? 100 : i < 14 ? 95 : 1).toArray();

    assertThat(
            journal(
                stats()
                    .dailyClicks(days("07", steadyThenTodaySoFar))
                    .today(LocalDate.parse("2026-07-15"))))
        .noneMatch(i -> i.code().startsWith("trend"));
  }

  @Test
  void peakDaySparkKeepsTheQuietDaysBeforeThePeak() {
    List<LinkStats.DailyClick> gaps =
        List.of(
            new LinkStats.DailyClick(LocalDate.parse("2026-07-01"), 1),
            new LinkStats.DailyClick(LocalDate.parse("2026-07-05"), 9));

    assertThat(entry("peakDay", stats().dailyClicks(gaps)).orElseThrow().spark())
        .containsExactly(1L, 0L, 0L, 0L, 9L);
  }

  @Test
  void sortsByImportanceAndKeepsFive() {
    long[] counts = IntStream.range(0, 14).mapToLong(i -> i == 13 ? 50 : 10).toArray();
    List<LinkStats.Insight> entries =
        journal(
            stats()
                .velocity(new LinkStats.Velocity(0, 0, 2.1))
                .peakHour(21)
                .timeToFirstClickMinutes(5L)
                .bot(25)
                .human(75)
                .returnRate(new LinkStats.ReturnRate(60, 40, 0.4))
                .dailyClicks(days("07", counts))
                .referrerHosts(
                    List.of(
                        new LinkStats.ReferrerHostClick("x.com", 80),
                        new LinkStats.ReferrerHostClick("b.com", 20)))
                .countries(
                    List.of(
                        new LinkStats.CountryClick("KR", 90), new LinkStats.CountryClick("JP", 10)))
                .devices(
                    List.of(
                        new LinkStats.DeviceClick("Mobile", 80),
                        new LinkStats.DeviceClick("Desktop", 20))));

    assertThat(entries).hasSize(5);
    assertThat(entries)
        .extracting(LinkStats.Insight::code)
        .containsExactly("velocity", "trendUp", "peakDay", "topSource", "botHigh");
    assertThat(entries.getFirst().params()).containsEntry("x", 2.1);
  }

  @Test
  void inAppShareUsesHumanClicksWithATwentyPercentFloorAndNamesNoApp() {
    assertThat(
            entry(
                "inAppBrowser",
                stats()
                    .human(100)
                    .clientApps(List.of(new LinkStats.ClientAppClick("kakaotalk", 19)))))
        .isEmpty();

    LinkStats.Insight over =
        entry(
                "inAppBrowser",
                stats()
                    .human(100)
                    .clientApps(
                        List.of(
                            new LinkStats.ClientAppClick("instagram", 9),
                            new LinkStats.ClientAppClick("kakaotalk", 12))))
            .orElseThrow();
    assertThat(over.params()).isEqualTo(Map.of("percent", 21L));
    assertThat(over.evidence()).isEqualTo("clientApps");
  }

  @Test
  void channelLoyaltyPicksTheBestReturnRatioAboveTheSampleFloor() {
    LinkStats.Insight loyal =
        entry(
                "channelLoyalty",
                stats()
                    .channelDepth(
                        List.of(
                            depth("tiny.example", 9, 4, 0.9),
                            depth("notion.site", 120, 41, 0.41),
                            depth("instagram.com", 400, 38, 0.11))))
            .orElseThrow();

    assertThat(loyal.params()).isEqualTo(Map.of("host", "notion.site", "percent", 41L));
  }

  @Test
  void channelLoyaltyFoldsTheWholeLinkReturningSentence() {
    LinkStats.ReturnRate returnRate = new LinkStats.ReturnRate(60, 40, 0.4);
    LinkStats.ChannelDepth loyal = depth("notion.site", 120, 41, 0.41);

    assertThat(entry("returning", stats().returnRate(returnRate).channelDepth(List.of(loyal))))
        .isEmpty();
    assertThat(
            entry(
                "returning",
                stats()
                    .returnRate(returnRate)
                    .channelDepth(List.of(depth("notion.site", 120, 41, 0.12)))))
        .isPresent();
  }

  @Test
  void peakDayGivesTheDateAndTheSparkUpToThePeak() {
    LinkStats.Insight peak =
        entry("peakDay", stats().dailyClicks(days("07", 1, 9, 3))).orElseThrow();

    assertThat(peak.params()).isEqualTo(Map.of("date", "2026-07-02", "count", 9L));
    assertThat(peak.spark()).containsExactly(1L, 9L);
  }

  @Test
  void peakDayNeedsTheSampleFloorInItsDailyWindow() {
    assertThat(entry("peakDay", stats().dailyClicks(days("07", 1, 2, 3)))).isEmpty();
    assertThat(entry("peakDay", stats().dailyClicks(days("07", 1, 2, 7))).orElseThrow().params())
        .isEqualTo(Map.of("date", "2026-07-03", "count", 7L));
  }

  @Test
  void peakHourNeedsTheHumanSampleFloor() {
    assertThat(entry("peakHour", stats().human(9).peakHour(21))).isEmpty();
    assertThat(entry("peakHour", stats().human(10).peakHour(21)).orElseThrow().params())
        .isEqualTo(Map.of("hour", 21));
  }

  @Nested
  class RealResponseShapeDividesByHumanClicks {

    @Test
    void inAppTotal349OfHuman1125Is31() {
      assertThat(entry("inAppBrowser", realLinkStats()).orElseThrow().params())
          .isEqualTo(Map.of("percent", 31L));
    }

    @Test
    void topSourceInstagram383OfHuman1125Is34WithDirectInTheDenominator() {
      assertThat(entry("topSource", realLinkStats()).orElseThrow().params())
          .isEqualTo(Map.of("host", "instagram.com", "percent", 34L));
    }

    @Test
    void oneReferredClickAmongDirectOnesIsNotMostOfTheTraffic() {
      assertThat(
              entry(
                  "topSource",
                  realLinkStats()
                      .human(1000)
                      .referrerHosts(List.of(new LinkStats.ReferrerHostClick("x.com", 1)))))
          .isEmpty();
    }

    @Test
    void topSourceNeedsTheHumanSampleFloor() {
      assertThat(
              entry(
                  "topSource",
                  realLinkStats()
                      .human(6)
                      .referrerHosts(List.of(new LinkStats.ReferrerHostClick("x.com", 6)))))
          .isEmpty();
    }

    @Test
    void sixPreviewsAreNotAHundredPercentAutomated() {
      assertThat(journal(realLinkStats().total(6).human(0).bot(6)))
          .noneMatch(i -> i.code().startsWith("bot"));
    }

    @Test
    void trendNeedsTheSampleFloorInThePreviousWeek() {
      assertThat(entry("trendUp", realLinkStats().dailyClicks(weeks(1, 3)))).isEmpty();
      assertThat(
              entry("trendUp", realLinkStats().dailyClicks(weeks(20, 30))).orElseThrow().params())
          .isEqualTo(Map.of("percent", 50L, "count", 30L));
    }

    private List<LinkStats.DailyClick> weeks(long prev, long last) {
      long[] counts =
          IntStream.range(0, 14).mapToLong(i -> i == 0 ? prev : i == 13 ? last : 0).toArray();
      return days("10", counts);
    }

    @Test
    void desktopSentenceCountsDesktopClicksOnly() {
      LinkInsights.ReportFacts.ReportFactsBuilder mixed =
          realLinkStats()
              .human(100)
              .devices(
                  List.of(
                      new LinkStats.DeviceClick("mobile", 30),
                      new LinkStats.DeviceClick("desktop", 30),
                      new LinkStats.DeviceClick("tablet", 20),
                      new LinkStats.DeviceClick("unknown", 20)));

      assertThat(entry("desktopHeavy", mixed)).isEmpty();
      assertThat(entry("mobileHeavy", realLinkStats()).orElseThrow().params())
          .isEqualTo(Map.of("percent", 78L));
    }

    @Test
    void countrySentenceSkipsUnknownAndDividesByHumanClicks() {
      assertThat(entry("topCountry", realLinkStats()).orElseThrow().params())
          .isEqualTo(Map.of("country", "KR", "percent", 72L));
      assertThat(
              entry(
                  "topCountry",
                  realLinkStats()
                      .countries(
                          List.of(
                              new LinkStats.CountryClick("unknown", 700),
                              new LinkStats.CountryClick("KR", 425)))))
          .isEmpty();
    }

    @Test
    void returningRatioNamesTheRawRetentionWindow() {
      LinkStats.ReturnRate rate = new LinkStats.ReturnRate(300, 60, 0.2);

      assertThat(entry("returningRecent", realLinkStats().returnRate(rate)).orElseThrow().params())
          .isEqualTo(Map.of("percent", 20L, "days", 90));
      assertThat(
              entry("returning", realLinkStats().rawSince(null).returnRate(rate))
                  .orElseThrow()
                  .params())
          .isEqualTo(Map.of("percent", 20L));
      assertThat(
              entry(
                  "returningRecent",
                  realLinkStats().returnRate(new LinkStats.ReturnRate(4, 2, 0.33))))
          .isEmpty();
    }
  }

  @Test
  void legacyMessageUsesTheSameFactsInEachLanguageWithoutRawCodes() {
    LocaleContextHolder.setLocale(Locale.KOREAN);
    List<LinkStats.Insight> ko = journal(realLinkStats());
    assertThat(ko)
        .extracting(LinkStats.Insight::code)
        .containsExactly(
            "inAppBrowser", "topSource", "returningRecent", "topCountry", "mobileHeavy");
    assertThat(ko)
        .extracting(LinkStats.Insight::message)
        .containsExactly(
            "클릭의 31%가 앱 안 브라우저에서 열렸어요",
            "유입의 34%가 instagram.com에서 왔어요",
            "최근 90일 다녀간 사람 17%가 다시 왔어요",
            "대한민국에서 72%가 왔어요",
            "클릭의 78%가 모바일에서 왔어요");

    LocaleContextHolder.setLocale(Locale.JAPANESE);
    assertThat(entry("topCountry", realLinkStats()).orElseThrow().message())
        .isEqualTo("韓国から72%が来ています");

    LocaleContextHolder.setLocale(Locale.ENGLISH);
    assertThat(entry("botFiltered", realLinkStats().returnRate(null)).orElseThrow().message())
        .isEqualTo("Excluding 14% automated traffic leaves 1,125 human clicks");
  }

  @Test
  void legacyFieldsStayFilledForOldClients() {
    LinkStats.Insight peak =
        entry("peakDay", stats().dailyClicks(days("07", 1, 9, 3))).orElseThrow();
    LocaleContextHolder.setLocale(Locale.KOREAN);

    assertThat(peak.type()).isEqualTo("PEAK_DAY");
    assertThat(peak.severity()).isEqualTo("info");
    assertThat(peak.data()).isEqualTo(peak.params());
    assertThat(entry("trendDown", stats().dailyClicks(days("07", trendDown()))).orElseThrow())
        .satisfies(
            down -> {
              assertThat(down.type()).isEqualTo("TREND_DOWN");
              assertThat(down.severity()).isEqualTo("warning");
            });
    assertThat(entry("peakDay", stats().dailyClicks(days("07", 1, 9, 3))).orElseThrow().message())
        .isEqualTo("07-02에 하루 9클릭으로 가장 많았어요");
  }

  private static long[] trendDown() {
    return IntStream.range(0, 14).mapToLong(i -> i < 7 ? 10 : 5).toArray();
  }

  @Test
  void velocityNeedsThirtyHumanClicks() {
    assertThat(entry("velocity", stats().human(29).velocity(new LinkStats.Velocity(9, 1, 9.0))))
        .isEmpty();
    assertThat(entry("velocity", stats().human(30).velocity(new LinkStats.Velocity(9, 1, 1.46))))
        .isEmpty();
  }

  @Test
  void fastFirstClickIsWithinTheFirstHourAndAtLeastOneMinute() {
    assertThat(entry("fastFirstClick", stats().timeToFirstClickMinutes(0L)).orElseThrow().params())
        .isEqualTo(Map.of("minutes", 1L));
    assertThat(entry("fastFirstClick", stats().timeToFirstClickMinutes(60L))).isEmpty();
    assertThat(entry("fastFirstClick", stats().timeToFirstClickMinutes(-1L))).isEmpty();
  }

  @Test
  void automatedShareAboveTwentyPercentIsAWarning() {
    LinkStats.Insight high = entry("botHigh", stats().bot(25).human(75)).orElseThrow();

    assertThat(high.params()).isEqualTo(Map.of("percent", 25L, "human", 75L));
    assertThat(high.evidence()).isEqualTo("automated");
    assertThat(high.ratio()).isEqualTo(0.25);
  }

  private static LinkInsights.ReportFacts.ReportFactsBuilder quiet() {
    return LinkInsights.ReportFacts.builder()
        .total(100)
        .human(100)
        .bot(0)
        .today(LocalDate.parse("2026-07-15"));
  }

  private static List<LinkStats.DailyClick> weekOverWeek(long previous, long last) {
    return List.of(
        new LinkStats.DailyClick(LocalDate.parse("2026-07-01"), previous),
        new LinkStats.DailyClick(LocalDate.parse("2026-07-14"), last));
  }

  private static Arguments rule(
      String code, boolean speaks, String at, LinkInsights.ReportFacts.ReportFactsBuilder facts) {
    return Arguments.of(code, speaks, at, facts);
  }

  static Stream<Arguments> thresholds() {
    LinkStats.Velocity fast = new LinkStats.Velocity(15, 10, 1.5);
    LinkStats.Velocity almost = new LinkStats.Velocity(15, 10, 1.4999);
    return Stream.of(
        rule("velocity", true, "1.5x with 30 humans", quiet().human(30).velocity(fast)),
        rule("velocity", false, "just under 1.5x", quiet().human(30).velocity(almost)),
        rule("velocity", false, "29 humans", quiet().human(29).velocity(fast)),
        rule("trendUp", true, "+15% over 20", quiet().dailyClicks(weekOverWeek(20, 23))),
        rule("trendUp", false, "+10% over 20", quiet().dailyClicks(weekOverWeek(20, 22))),
        rule("trendDown", true, "-15% over 20", quiet().dailyClicks(weekOverWeek(20, 17))),
        rule(
            "trendUp",
            true,
            "10 clicks the week before",
            quiet().dailyClicks(weekOverWeek(10, 30))),
        rule(
            "trendUp", false, "9 clicks the week before", quiet().dailyClicks(weekOverWeek(9, 30))),
        rule("topSource", true, "25%", quiet().referrerHosts(hosts(25))),
        rule("topSource", false, "24%", quiet().referrerHosts(hosts(24))),
        rule(
            "channelLoyalty",
            true,
            "30% of 5",
            quiet().channelDepth(List.of(depth("a.com", 20, 5, 0.3)))),
        rule(
            "channelLoyalty",
            false,
            "29% of 5",
            quiet().channelDepth(List.of(depth("a.com", 20, 5, 0.29)))),
        rule(
            "channelLoyalty",
            false,
            "30% of 4",
            quiet().channelDepth(List.of(depth("a.com", 20, 4, 0.3)))),
        rule("inAppBrowser", true, "20%", quiet().clientApps(apps(20))),
        rule("inAppBrowser", false, "19%", quiet().clientApps(apps(19))),
        rule(
            "returning",
            true,
            "15% of 10",
            quiet().returnRate(new LinkStats.ReturnRate(8, 2, 0.15))),
        rule(
            "returning",
            false,
            "14% of 10",
            quiet().returnRate(new LinkStats.ReturnRate(8, 2, 0.14))),
        rule(
            "returning",
            false,
            "15% of 9",
            quiet().returnRate(new LinkStats.ReturnRate(7, 2, 0.15))),
        rule(
            "returning",
            true,
            "a raw window but no retention days",
            quiet()
                .returnRate(new LinkStats.ReturnRate(8, 2, 0.15))
                .rawSince(Instant.parse("2026-04-16T00:00:00Z"))
                .rawRetentionDays(0)),
        rule("topCountry", true, "40%", quiet().countries(countries(40))),
        rule("topCountry", false, "39%", quiet().countries(countries(39))),
        rule("mobileHeavy", true, "60% mobile", quiet().devices(devices("mobile", 60))),
        rule("mobileHeavy", false, "59% mobile", quiet().devices(devices("mobile", 59))),
        rule("desktopHeavy", true, "60% desktop", quiet().devices(devices("desktop", 60))),
        rule("desktopHeavy", false, "59% desktop", quiet().devices(devices("desktop", 59))),
        rule("botHigh", true, "20% of 100", quiet().human(80).bot(20)),
        rule("botFiltered", true, "19% of 100", quiet().human(81).bot(19)),
        rule("botFiltered", true, "5% of 100", quiet().human(95).bot(5)),
        rule("botFiltered", false, "4% of 100", quiet().human(96).bot(4)),
        rule("botHigh", true, "20% of 10", quiet().total(10).human(8).bot(2)),
        rule("botHigh", false, "22% of 9", quiet().total(9).human(7).bot(2)),
        rule("peakDay", true, "10 clicks in the window", quiet().dailyClicks(days("07", 1, 2, 7))),
        rule("peakDay", false, "9 clicks in the window", quiet().dailyClicks(days("07", 1, 2, 6))),
        rule("fastFirstClick", true, "59 minutes", quiet().timeToFirstClickMinutes(59L)),
        rule("fastFirstClick", false, "60 minutes", quiet().timeToFirstClickMinutes(60L)));
  }

  @ParameterizedTest(name = "{0} speaks={1} at {2}")
  @MethodSource("thresholds")
  void eachRuleSpeaksFromItsThresholdAndNotJustBelowIt(
      String code, boolean speaks, String at, LinkInsights.ReportFacts.ReportFactsBuilder facts) {
    assertThat(entry(code, facts).isPresent()).as(at).isEqualTo(speaks);
  }

  @Test
  void theLatestOfTheBusiestDaysIsThePeakAndTheFirstDayCanBeIt() {
    assertThat(entry("peakDay", quiet().dailyClicks(days("07", 5, 5))).orElseThrow().params())
        .containsEntry("date", "2026-07-02");
    assertThat(entry("peakDay", quiet().dailyClicks(days("07", 9, 1))).orElseThrow().params())
        .containsEntry("date", "2026-07-01");
  }

  @Test
  void amongEquallyLoyalChannelsTheFirstListedIsNamed() {
    assertThat(
            entry(
                    "channelLoyalty",
                    quiet()
                        .channelDepth(
                            List.of(depth("a.com", 20, 5, 0.5), depth("b.com", 20, 5, 0.5))))
                .orElseThrow()
                .params())
        .containsEntry("host", "a.com");
  }

  @Test
  void aDeviceShareIsReadFromThatDeviceWhereverItIsListed() {
    assertThat(
            entry(
                "mobileHeavy",
                quiet()
                    .devices(
                        List.of(
                            new LinkStats.DeviceClick("desktop", 70),
                            new LinkStats.DeviceClick("mobile", 30)))))
        .isEmpty();
  }

  private static List<LinkStats.ReferrerHostClick> hosts(long top) {
    return List.of(
        new LinkStats.ReferrerHostClick("a.com", top), new LinkStats.ReferrerHostClick("b.com", 1));
  }

  private static List<LinkStats.ClientAppClick> apps(long count) {
    return List.of(new LinkStats.ClientAppClick("kakaotalk", count));
  }

  private static List<LinkStats.CountryClick> countries(long top) {
    return List.of(
        new LinkStats.CountryClick("KR", top), new LinkStats.CountryClick("JP", 100 - top));
  }

  private static List<LinkStats.DeviceClick> devices(String device, long count) {
    return List.of(new LinkStats.DeviceClick(device, count));
  }
}
