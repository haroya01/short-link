package com.example.short_link.link.stats.application;

import com.example.short_link.link.application.dto.LinkStats;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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
    darkSocial(channels, total).ifPresent(insights::add);
    secondWind(dailyClicks).ifPresent(insights::add);
    dormancy(dailyClicks).ifPresent(insights::add);
    return insights;
  }

  /// 직접 공유(다크 소셜) — referrer 없는 사람 클릭 비율 = DM·복붙으로 퍼진 입소문 신호.
  private Optional<LinkStats.Insight> darkSocial(
      List<LinkStats.ChannelClick> channels, long total) {
    if (total == 0) return Optional.empty();
    long direct = 0;
    for (LinkStats.ChannelClick c : channels) {
      if ("direct".equals(c.channel())) direct += c.count();
    }
    double share = (double) direct / total;
    if (share < 0.3) return Optional.empty();
    String message =
        String.format(Locale.KOREAN, "직접 공유(다크소셜)가 %.1f%% — DM·복붙으로 퍼졌어요", share * 100);
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("share", round3(share));
    data.put("directClicks", direct);
    return Optional.of(new LinkStats.Insight("DARK_SOCIAL", "info", message, data));
  }

  /// 재점화(second wind) — 잠잠하던 링크가 최근 며칠 확 뜀(휴면 후 부활).
  private Optional<LinkStats.Insight> secondWind(List<LinkStats.DailyClick> dailyClicks) {
    int n = dailyClicks.size();
    if (n < 10) return Optional.empty();
    long recent3 = 0;
    for (int i = Math.max(0, n - 3); i < n; i++) recent3 += dailyClicks.get(i).count();
    int ds = Math.max(0, n - 10);
    int de = Math.max(0, n - 3);
    long dormant = 0;
    for (int i = ds; i < de; i++) dormant += dailyClicks.get(i).count();
    double dormantAvg = (de - ds) > 0 ? (double) dormant / (de - ds) : 0;
    if (recent3 >= 5 && dormantAvg < 1.0 && recent3 >= 3 * Math.max(dormant, 1)) {
      String message = String.format(Locale.KOREAN, "다시 살아났어요 — 잠잠하던 링크가 최근 %d클릭", recent3);
      Map<String, Object> data = new LinkedHashMap<>();
      data.put("recent3", recent3);
      data.put("dormantAvg", round3(dormantAvg));
      return Optional.of(new LinkStats.Insight("SECOND_WIND", "info", message, data));
    }
    return Optional.empty();
  }

  /// 휴면(dormancy) — 마지막 사람 클릭 이후 N일 조용. 콜드 링크 재공유 넛지의 단일 진실원.
  private Optional<LinkStats.Insight> dormancy(List<LinkStats.DailyClick> dailyClicks) {
    java.time.LocalDate lastActive = null;
    for (LinkStats.DailyClick dc : dailyClicks) {
      if (dc.count() > 0) lastActive = dc.date();
    }
    if (lastActive == null) return Optional.empty();
    long daysIdle =
        java.time.temporal.ChronoUnit.DAYS.between(lastActive, java.time.LocalDate.now());
    if (daysIdle < 7) return Optional.empty();
    String message = String.format(Locale.KOREAN, "%d일째 클릭이 없어요 — 다시 공유하면 살아날 수 있어요", daysIdle);
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("daysIdle", daysIdle);
    return Optional.of(new LinkStats.Insight("DORMANT", "warning", message, data));
  }

  private Optional<LinkStats.Insight> peakHour(List<LinkStats.HeatmapCell> heatmap, long total) {
    if (heatmap.isEmpty()) return Optional.empty();
    LinkStats.HeatmapCell peak = heatmap.get(0);
    for (LinkStats.HeatmapCell cell : heatmap) {
      if (cell.count() > peak.count()) peak = cell;
    }
    if (peak.count() < 2) return Optional.empty();
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
    return Optional.of(new LinkStats.Insight("PEAK_HOUR", "info", message, data));
  }

  private Optional<LinkStats.Insight> topChannel(
      List<LinkStats.ChannelClick> channels, long total) {
    if (channels.isEmpty()) return Optional.empty();
    LinkStats.ChannelClick top = channels.get(0);
    double share = (double) top.count() / total;
    if (share < TOP_CHANNEL_THRESHOLD) return Optional.empty();
    String message =
        String.format(Locale.KOREAN, "트래픽의 %.1f%%가 %s에서 발생", share * 100, top.channel());
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("channel", top.channel());
    data.put("share", round3(share));
    return Optional.of(new LinkStats.Insight("TOP_CHANNEL", "info", message, data));
  }

  private Optional<LinkStats.Insight> countryConcentration(
      List<LinkStats.CountryClick> countries, long total) {
    if (countries.isEmpty()) return Optional.empty();
    LinkStats.CountryClick top = countries.get(0);
    double share = (double) top.count() / total;
    if (share < TOP_COUNTRY_THRESHOLD) return Optional.empty();
    String message =
        String.format(Locale.KOREAN, "클릭의 %.1f%%가 %s에서 발생, 글로벌 도달이 낮음", share * 100, top.country());
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("country", top.country());
    data.put("share", round3(share));
    return Optional.of(new LinkStats.Insight("COUNTRY_CONCENTRATION", "info", message, data));
  }

  private Optional<LinkStats.Insight> visitorMix(LinkStats.ReturnRate rr) {
    if (rr == null) return Optional.empty();
    long denom = rr.newVisitors() + rr.returningVisitors();
    if (denom < 5) return Optional.empty();
    double newShare = (double) rr.newVisitors() / denom;
    String message = String.format(Locale.KOREAN, "방문자 %.1f%%가 첫 방문", newShare * 100);
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("newShare", round3(newShare));
    data.put("returningShare", round3(1.0 - newShare));
    return Optional.of(new LinkStats.Insight("VISITOR_MIX", "info", message, data));
  }

  private Optional<LinkStats.Insight> botRatio(long bot, long total) {
    if (total == 0) return Optional.empty();
    double share = (double) bot / total;
    if (share < BOT_RATIO_THRESHOLD) return Optional.empty();
    String message = String.format(Locale.KOREAN, "봇 트래픽이 %.1f%%로 비정상적으로 높음", share * 100);
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("share", round3(share));
    return Optional.of(new LinkStats.Insight("BOT_RATIO_HIGH", "warning", message, data));
  }

  private Optional<LinkStats.Insight> decayShape(LinkStats.Lifecycle lifecycle) {
    if (lifecycle == null || lifecycle.dayClicks().isEmpty()) return Optional.empty();
    long total = 0;
    long firstDay = 0;
    long firstWeek = 0;
    for (LinkStats.DayClick dc : lifecycle.dayClicks()) {
      total += dc.count();
      if (dc.day() == 0) firstDay = dc.count();
      if (dc.day() <= 6) firstWeek += dc.count();
    }
    if (total < 10 || firstWeek == 0) return Optional.empty();
    double firstDayShare = (double) firstDay / total;
    if (firstDayShare >= 1 - FAST_DECAY_THRESHOLD) {
      String message =
          String.format(Locale.KOREAN, "빠른 소멸형 (1일 이내 %.1f%% 소진)", firstDayShare * 100);
      Map<String, Object> data = new LinkedHashMap<>();
      data.put("firstDayShare", round3(firstDayShare));
      data.put("halfLifeDays", lifecycle.halfLifeDays());
      return Optional.of(new LinkStats.Insight("FAST_DECAY", "info", message, data));
    }
    return Optional.empty();
  }

  private Optional<LinkStats.Insight> weekOverWeek(List<LinkStats.DailyClick> dailyClicks) {
    if (dailyClicks.size() < 8) return Optional.empty();
    int n = dailyClicks.size();
    long last7 = 0;
    long prev7 = 0;
    for (int i = Math.max(0, n - 7); i < n; i++) last7 += dailyClicks.get(i).count();
    int prevStart = Math.max(0, n - 14);
    int prevEnd = Math.max(0, n - 7);
    for (int i = prevStart; i < prevEnd; i++) prev7 += dailyClicks.get(i).count();
    if (prev7 < 5) return Optional.empty();
    double ratio = (double) (last7 - prev7) / prev7;
    if (Math.abs(ratio) < 0.2) return Optional.empty();
    String direction = ratio > 0 ? "+" : "";
    String message =
        String.format(Locale.KOREAN, "지난 7일 클릭 %s%.1f%% (전 7일 대비)", direction, ratio * 100);
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("last7", last7);
    data.put("prev7", prev7);
    data.put("ratio", round3(ratio));
    String severity = ratio > 0 ? "info" : "warning";
    return Optional.of(new LinkStats.Insight("WEEK_OVER_WEEK", severity, message, data));
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
