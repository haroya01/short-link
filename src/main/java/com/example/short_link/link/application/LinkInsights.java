package com.example.short_link.link.application;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class LinkInsights {

  private static final long MIN_TOTAL_FOR_INSIGHTS = 10;
  private static final double TOP_CHANNEL_THRESHOLD = 0.6;
  private static final double TOP_COUNTRY_THRESHOLD = 0.8;
  private static final double BOT_RATIO_THRESHOLD = 0.3;
  private static final double FAST_DECAY_THRESHOLD = 0.2;

  public List<LinkStats.Insight> compute(
      long total,
      long bot,
      List<LinkStats.HeatmapCell> heatmap,
      List<LinkStats.ChannelClick> channels,
      List<LinkStats.CountryClick> countries,
      LinkStats.ReturnRate returnRate,
      LinkStats.Lifecycle lifecycle,
      List<LinkStats.DailyClick> dailyClicks) {
    List<LinkStats.Insight> insights = new ArrayList<>();
    if (total < MIN_TOTAL_FOR_INSIGHTS) {
      return insights;
    }

    peakHour(heatmap, total).ifPresent(insights::add);
    topChannel(channels, total).ifPresent(insights::add);
    countryConcentration(countries, total).ifPresent(insights::add);
    visitorMix(returnRate).ifPresent(insights::add);
    botRatio(bot, total).ifPresent(insights::add);
    decayShape(lifecycle).ifPresent(insights::add);
    weekOverWeek(dailyClicks).ifPresent(insights::add);
    return insights;
  }

  private java.util.Optional<LinkStats.Insight> peakHour(
      List<LinkStats.HeatmapCell> heatmap, long total) {
    if (heatmap.isEmpty()) return java.util.Optional.empty();
    LinkStats.HeatmapCell peak = heatmap.get(0);
    for (LinkStats.HeatmapCell cell : heatmap) {
      if (cell.count() > peak.count()) peak = cell;
    }
    if (peak.count() < 2) return java.util.Optional.empty();
    double share = (double) peak.count() / total;
    String dow = peak.dayOfWeek();
    String dowKo = dayOfWeekKo(dow);
    String message =
        String.format(Locale.KOREAN, "피크 시간은 %s %d시 (전체의 %.1f%%)", dowKo, peak.hour(), share * 100);
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("dayOfWeek", dow);
    data.put("hour", peak.hour());
    data.put("count", peak.count());
    data.put("share", round3(share));
    return java.util.Optional.of(new LinkStats.Insight("PEAK_HOUR", "info", message, data));
  }

  private java.util.Optional<LinkStats.Insight> topChannel(
      List<LinkStats.ChannelClick> channels, long total) {
    if (channels.isEmpty()) return java.util.Optional.empty();
    LinkStats.ChannelClick top = channels.get(0);
    double share = (double) top.count() / total;
    if (share < TOP_CHANNEL_THRESHOLD) return java.util.Optional.empty();
    String message =
        String.format(Locale.KOREAN, "트래픽의 %.1f%%가 %s에서 발생", share * 100, top.channel());
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("channel", top.channel());
    data.put("share", round3(share));
    return java.util.Optional.of(new LinkStats.Insight("TOP_CHANNEL", "info", message, data));
  }

  private java.util.Optional<LinkStats.Insight> countryConcentration(
      List<LinkStats.CountryClick> countries, long total) {
    if (countries.isEmpty()) return java.util.Optional.empty();
    LinkStats.CountryClick top = countries.get(0);
    double share = (double) top.count() / total;
    if (share < TOP_COUNTRY_THRESHOLD) return java.util.Optional.empty();
    String message =
        String.format(Locale.KOREAN, "클릭의 %.1f%%가 %s에서 발생, 글로벌 도달이 낮음", share * 100, top.country());
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("country", top.country());
    data.put("share", round3(share));
    return java.util.Optional.of(
        new LinkStats.Insight("COUNTRY_CONCENTRATION", "info", message, data));
  }

  private java.util.Optional<LinkStats.Insight> visitorMix(LinkStats.ReturnRate rr) {
    if (rr == null) return java.util.Optional.empty();
    long denom = rr.newVisitors() + rr.returningVisitors();
    if (denom < 5) return java.util.Optional.empty();
    double newShare = (double) rr.newVisitors() / denom;
    String message = String.format(Locale.KOREAN, "방문자 %.1f%%가 첫 방문", newShare * 100);
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("newShare", round3(newShare));
    data.put("returningShare", round3(1.0 - newShare));
    return java.util.Optional.of(new LinkStats.Insight("VISITOR_MIX", "info", message, data));
  }

  private java.util.Optional<LinkStats.Insight> botRatio(long bot, long total) {
    if (total == 0) return java.util.Optional.empty();
    double share = (double) bot / total;
    if (share < BOT_RATIO_THRESHOLD) return java.util.Optional.empty();
    String message = String.format(Locale.KOREAN, "봇 트래픽이 %.1f%%로 비정상적으로 높음", share * 100);
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("share", round3(share));
    return java.util.Optional.of(new LinkStats.Insight("BOT_RATIO_HIGH", "warning", message, data));
  }

  private java.util.Optional<LinkStats.Insight> decayShape(LinkStats.Lifecycle lifecycle) {
    if (lifecycle == null || lifecycle.dayClicks().isEmpty()) return java.util.Optional.empty();
    long total = 0;
    long firstDay = 0;
    long firstWeek = 0;
    for (LinkStats.DayClick dc : lifecycle.dayClicks()) {
      total += dc.count();
      if (dc.day() == 0) firstDay = dc.count();
      if (dc.day() <= 6) firstWeek += dc.count();
    }
    if (total < 10 || firstWeek == 0) return java.util.Optional.empty();
    double firstDayShare = (double) firstDay / total;
    if (firstDayShare >= 1 - FAST_DECAY_THRESHOLD) {
      String message =
          String.format(Locale.KOREAN, "빠른 소멸형 (1일 이내 %.1f%% 소진)", firstDayShare * 100);
      Map<String, Object> data = new LinkedHashMap<>();
      data.put("firstDayShare", round3(firstDayShare));
      data.put("halfLifeDays", lifecycle.halfLifeDays());
      return java.util.Optional.of(new LinkStats.Insight("FAST_DECAY", "info", message, data));
    }
    return java.util.Optional.empty();
  }

  private java.util.Optional<LinkStats.Insight> weekOverWeek(
      List<LinkStats.DailyClick> dailyClicks) {
    if (dailyClicks.size() < 8) return java.util.Optional.empty();
    int n = dailyClicks.size();
    long last7 = 0;
    long prev7 = 0;
    for (int i = Math.max(0, n - 7); i < n; i++) last7 += dailyClicks.get(i).count();
    int prevStart = Math.max(0, n - 14);
    int prevEnd = Math.max(0, n - 7);
    for (int i = prevStart; i < prevEnd; i++) prev7 += dailyClicks.get(i).count();
    if (prev7 < 5) return java.util.Optional.empty();
    double ratio = (double) (last7 - prev7) / prev7;
    if (Math.abs(ratio) < 0.2) return java.util.Optional.empty();
    String direction = ratio > 0 ? "+" : "";
    String message =
        String.format(Locale.KOREAN, "지난 7일 클릭 %s%.1f%% (전 7일 대비)", direction, ratio * 100);
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("last7", last7);
    data.put("prev7", prev7);
    data.put("ratio", round3(ratio));
    String severity = ratio > 0 ? "info" : "warning";
    return java.util.Optional.of(new LinkStats.Insight("WEEK_OVER_WEEK", severity, message, data));
  }

  private static double round3(double v) {
    return Math.round(v * 1000.0) / 1000.0;
  }

  private static String dayOfWeekKo(String enumName) {
    try {
      DayOfWeek dow = DayOfWeek.valueOf(enumName);
      return switch (dow) {
        case MONDAY -> "월요일";
        case TUESDAY -> "화요일";
        case WEDNESDAY -> "수요일";
        case THURSDAY -> "목요일";
        case FRIDAY -> "금요일";
        case SATURDAY -> "토요일";
        case SUNDAY -> "일요일";
      };
    } catch (IllegalArgumentException e) {
      return enumName;
    }
  }
}
