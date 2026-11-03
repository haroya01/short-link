package com.example.short_link.link.stats.application;

import com.example.short_link.link.application.dto.LinkStats;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.Builder;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

@Component
public class LinkInsights {

  private static final long MIN_TOTAL_FOR_INSIGHTS = 10;
  private static final long MIN_HUMAN_FOR_VELOCITY = 30;
  private static final double VELOCITY_THRESHOLD = 1.5;
  private static final double TREND_THRESHOLD = 0.15;
  private static final double TOP_SOURCE_THRESHOLD = 0.25;
  private static final double IN_APP_SHARE_THRESHOLD = 0.2;
  private static final double CHANNEL_LOYALTY_THRESHOLD = 0.3;
  private static final long CHANNEL_LOYALTY_MIN_VISITORS = 5;
  private static final double RETURNING_THRESHOLD = 0.15;
  private static final double TOP_COUNTRY_THRESHOLD = 0.4;
  private static final double DEVICE_THRESHOLD = 0.6;
  private static final double AUTOMATED_HIGH_THRESHOLD = 0.2;
  private static final double AUTOMATED_SHOWN_THRESHOLD = 0.05;
  private static final long FAST_FIRST_CLICK_MINUTES = 60;
  private static final int MAX_INSIGHTS = 5;

  private final MessageSource messages;

  public LinkInsights(MessageSource messages) {
    this.messages = messages;
  }

  @Builder
  public record ReportFacts(
      long total,
      long human,
      long bot,
      List<LinkStats.DailyClick> dailyClicks,
      LinkStats.Velocity velocity,
      List<LinkStats.ReferrerHostClick> referrerHosts,
      List<LinkStats.ChannelDepth> channelDepth,
      List<LinkStats.ClientAppClick> clientApps,
      Integer peakHour,
      LinkStats.ReturnRate returnRate,
      Instant rawSince,
      int rawRetentionDays,
      List<LinkStats.CountryClick> countries,
      List<LinkStats.DeviceClick> devices,
      Long timeToFirstClickMinutes) {
    public ReportFacts {
      dailyClicks = orEmpty(dailyClicks);
      referrerHosts = orEmpty(referrerHosts);
      channelDepth = orEmpty(channelDepth);
      clientApps = orEmpty(clientApps);
      countries = orEmpty(countries);
      devices = orEmpty(devices);
    }

    boolean enoughHuman() {
      return human >= MIN_TOTAL_FOR_INSIGHTS;
    }

    private static <T> List<T> orEmpty(List<T> values) {
      return values == null ? List.of() : values;
    }
  }

  private record Fact(
      String code,
      Map<String, Object> params,
      String evidence,
      Double ratio,
      List<Long> spark,
      int weight) {}

  public List<LinkStats.Insight> computeReport(ReportFacts facts) {
    if (facts.total() == 0) return List.of();
    List<Fact> found = new ArrayList<>();
    velocity(facts).ifPresent(found::add);
    trend(facts.dailyClicks()).ifPresent(found::add);
    peakDay(facts.dailyClicks()).ifPresent(found::add);
    topSource(facts).ifPresent(found::add);
    Optional<Fact> loyalty = channelLoyalty(facts.channelDepth());
    loyalty.ifPresent(found::add);
    inAppBrowser(facts).ifPresent(found::add);
    peakHour(facts.peakHour()).ifPresent(found::add);
    if (loyalty.isEmpty()) returning(facts).ifPresent(found::add);
    topCountry(facts).ifPresent(found::add);
    device(facts).ifPresent(found::add);
    automated(facts).ifPresent(found::add);
    fastFirstClick(facts.timeToFirstClickMinutes()).ifPresent(found::add);
    return found.stream()
        .sorted(Comparator.comparingInt(Fact::weight).reversed())
        .limit(MAX_INSIGHTS)
        .map(this::toInsight)
        .toList();
  }

  private static Optional<Fact> velocity(ReportFacts facts) {
    double ratio = facts.velocity() == null ? 0 : facts.velocity().ratio();
    if (ratio < VELOCITY_THRESHOLD || facts.human() < MIN_HUMAN_FOR_VELOCITY) {
      return Optional.empty();
    }
    return fact("velocity", "hourly", null, null, 100, "x", Math.round(ratio * 10) / 10.0);
  }

  private static Optional<Fact> trend(List<LinkStats.DailyClick> daily) {
    int n = daily.size();
    long last7 = sum(daily, n - 7, n);
    long prev7 = sum(daily, n - 14, n - 7);
    if (prev7 < MIN_TOTAL_FOR_INSIGHTS) return Optional.empty();
    double delta = (double) (last7 - prev7) / prev7;
    if (Math.abs(delta) < TREND_THRESHOLD) return Optional.empty();
    return fact(
        delta >= 0 ? "trendUp" : "trendDown",
        "daily",
        null,
        counts(daily, n - 14, n),
        90,
        "percent",
        Math.abs(Math.round(delta * 100)),
        "count",
        last7);
  }

  private static Optional<Fact> peakDay(List<LinkStats.DailyClick> daily) {
    int peakIndex = -1;
    for (int i = 0; i < daily.size(); i++) {
      if (peakIndex < 0 || daily.get(i).count() >= daily.get(peakIndex).count()) peakIndex = i;
    }
    if (peakIndex < 0 || daily.get(peakIndex).count() <= 0) return Optional.empty();
    LinkStats.DailyClick peak = daily.get(peakIndex);
    return fact(
        "peakDay",
        "daily",
        null,
        counts(daily, peakIndex - 6, peakIndex + 1),
        80,
        "date",
        peak.date().toString(),
        "count",
        peak.count());
  }

  private static Optional<Fact> topSource(ReportFacts facts) {
    if (facts.referrerHosts().isEmpty() || !facts.enoughHuman()) return Optional.empty();
    LinkStats.ReferrerHostClick top = facts.referrerHosts().getFirst();
    double share = (double) top.count() / facts.human();
    if (share < TOP_SOURCE_THRESHOLD) return Optional.empty();
    return fact("topSource", "sources", share, null, 75, "host", top.host(), "percent", pct(share));
  }

  private static Optional<Fact> channelLoyalty(List<LinkStats.ChannelDepth> channelDepth) {
    LinkStats.ChannelDepth loyal = null;
    for (LinkStats.ChannelDepth c : channelDepth) {
      if (c.returningVisitors() < CHANNEL_LOYALTY_MIN_VISITORS) continue;
      if (c.returnRatio() < CHANNEL_LOYALTY_THRESHOLD) continue;
      if (loyal == null || c.returnRatio() > loyal.returnRatio()) loyal = c;
    }
    if (loyal == null) return Optional.empty();
    return fact(
        "channelLoyalty",
        "channelDepth",
        loyal.returnRatio(),
        null,
        82,
        "host",
        loyal.host(),
        "percent",
        pct(loyal.returnRatio()));
  }

  private static Optional<Fact> inAppBrowser(ReportFacts facts) {
    if (facts.clientApps().isEmpty() || !facts.enoughHuman()) return Optional.empty();
    long inApp = facts.clientApps().stream().mapToLong(LinkStats.ClientAppClick::count).sum();
    double share = (double) inApp / facts.human();
    if (share < IN_APP_SHARE_THRESHOLD) return Optional.empty();
    return fact("inAppBrowser", "clientApps", share, null, 78, "percent", pct(share));
  }

  private static Optional<Fact> peakHour(Integer hour) {
    if (hour == null) return Optional.empty();
    return fact("peakHour", "hourly", null, null, 70, "hour", hour);
  }

  private static Optional<Fact> returning(ReportFacts facts) {
    LinkStats.ReturnRate rate = facts.returnRate();
    if (rate == null) return Optional.empty();
    long visitors = rate.newVisitors() + rate.returningVisitors();
    if (rate.ratio() < RETURNING_THRESHOLD || visitors < MIN_TOTAL_FOR_INSIGHTS) {
      return Optional.empty();
    }
    if (facts.rawSince() != null && facts.rawRetentionDays() > 0) {
      return fact(
          "returningRecent",
          "visitors",
          rate.ratio(),
          null,
          65,
          "percent",
          pct(rate.ratio()),
          "days",
          facts.rawRetentionDays());
    }
    return fact("returning", "visitors", rate.ratio(), null, 65, "percent", pct(rate.ratio()));
  }

  private static Optional<Fact> topCountry(ReportFacts facts) {
    if (!facts.enoughHuman()) return Optional.empty();
    Optional<LinkStats.CountryClick> top =
        facts.countries().stream().filter(c -> !"unknown".equals(c.country())).findFirst();
    if (top.isEmpty()) return Optional.empty();
    double share = (double) top.get().count() / facts.human();
    if (share < TOP_COUNTRY_THRESHOLD) return Optional.empty();
    return fact(
        "topCountry",
        "countries",
        share,
        null,
        60,
        "country",
        top.get().country(),
        "percent",
        pct(share));
  }

  private static Optional<Fact> device(ReportFacts facts) {
    if (!facts.enoughHuman()) return Optional.empty();
    double mobile = deviceShare(facts, "mobile");
    if (mobile >= DEVICE_THRESHOLD) {
      return fact("mobileHeavy", "devices", mobile, null, 55, "percent", pct(mobile));
    }
    double desktop = deviceShare(facts, "desktop");
    if (desktop >= DEVICE_THRESHOLD) {
      return fact("desktopHeavy", "devices", desktop, null, 55, "percent", pct(desktop));
    }
    return Optional.empty();
  }

  private static double deviceShare(ReportFacts facts, String device) {
    long count =
        facts.devices().stream()
            .filter(d -> device.equals(d.device().toLowerCase(Locale.ROOT)))
            .findFirst()
            .map(LinkStats.DeviceClick::count)
            .orElse(0L);
    return (double) count / facts.human();
  }

  private static Optional<Fact> automated(ReportFacts facts) {
    if (facts.total() < MIN_TOTAL_FOR_INSIGHTS) return Optional.empty();
    double share = (double) facts.bot() / facts.total();
    if (share >= AUTOMATED_HIGH_THRESHOLD) {
      return fact(
          "botHigh", "automated", share, null, 72, "percent", pct(share), "human", facts.human());
    }
    if (share >= AUTOMATED_SHOWN_THRESHOLD) {
      return fact(
          "botFiltered",
          "automated",
          share,
          null,
          50,
          "percent",
          pct(share),
          "human",
          facts.human());
    }
    return Optional.empty();
  }

  private static Optional<Fact> fastFirstClick(Long minutes) {
    if (minutes == null || minutes < 0 || minutes >= FAST_FIRST_CLICK_MINUTES) {
      return Optional.empty();
    }
    return fact("fastFirstClick", "daily", null, null, 40, "minutes", Math.max(1, minutes));
  }

  private static Optional<Fact> fact(
      String code,
      String evidence,
      Double ratio,
      List<Long> spark,
      int weight,
      Object... keyValues) {
    Map<String, Object> params = new LinkedHashMap<>();
    for (int i = 0; i < keyValues.length; i += 2) {
      params.put((String) keyValues[i], keyValues[i + 1]);
    }
    return Optional.of(new Fact(code, params, evidence, ratio, spark, weight));
  }

  private LinkStats.Insight toInsight(Fact fact) {
    String message =
        messages.getMessage(
            "insight." + fact.code(), messageArgs(fact), LocaleContextHolder.getLocale());
    return new LinkStats.Insight(
        legacyType(fact.code()),
        "trendDown".equals(fact.code()) || "botHigh".equals(fact.code()) ? "warning" : "info",
        message,
        fact.params(),
        fact.code(),
        fact.params(),
        fact.evidence(),
        fact.ratio(),
        fact.spark());
  }

  private static Object[] messageArgs(Fact fact) {
    Locale locale = LocaleContextHolder.getLocale();
    return fact.params().entrySet().stream()
        .map(
            e ->
                switch (e.getKey()) {
                  case "x" -> String.format(Locale.ROOT, "%.1f", (Double) e.getValue());
                  case "date" -> ((String) e.getValue()).substring(5);
                  case "country" -> Locale.of("", (String) e.getValue()).getDisplayCountry(locale);
                  default -> e.getValue();
                })
        .toArray();
  }

  private static String legacyType(String code) {
    return code.replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase(Locale.ROOT);
  }

  private static long sum(List<LinkStats.DailyClick> daily, int from, int until) {
    long total = 0;
    for (int i = Math.max(0, from); i < Math.max(0, until); i++) total += daily.get(i).count();
    return total;
  }

  private static List<Long> counts(List<LinkStats.DailyClick> daily, int from, int until) {
    List<Long> counts = new ArrayList<>();
    for (int i = Math.max(0, from); i < Math.max(0, until); i++) counts.add(daily.get(i).count());
    return counts;
  }

  private static long pct(double ratio) {
    return Math.round(ratio * 100);
  }
}
