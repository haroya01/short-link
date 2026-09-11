package com.example.short_link.link.stats.application;

import com.example.short_link.link.application.dto.LinkStats;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.HostFirstSeenRow;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.Builder;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

@Component
public class LinkInsights {

  private static final long MIN_TOTAL_FOR_INSIGHTS = 10;
  private static final double TOP_CHANNEL_THRESHOLD = 0.6;
  private static final double TOP_COUNTRY_THRESHOLD = 0.8;
  private static final double BOT_RATIO_THRESHOLD = 0.3;
  private static final double FAST_DECAY_THRESHOLD = 0.2;

  private static final double IN_APP_SHARE_THRESHOLD = 0.2;

  private static final double CHANNEL_LOYALTY_THRESHOLD = 0.3;

  /** 적은 표본에서 비율이 크게 흔들리는 것을 막는다. */
  private static final long CHANNEL_LOYALTY_MIN_VISITORS = 5;

  private final MessageSource messages;

  public LinkInsights(MessageSource messages) {
    this.messages = messages;
  }

  @Builder
  public record ReportFacts(
      LocalDate reportDate,
      long total,
      long human,
      long bot,
      List<LinkStats.HeatmapCell> heatmap,
      List<LinkStats.ChannelClick> channels,
      List<LinkStats.CountryClick> countries,
      LinkStats.ReturnRate returnRate,
      LinkStats.Lifecycle lifecycle,
      List<LinkStats.DailyClick> dailyClicks,
      List<LinkStats.ClientAppClick> clientApps,
      List<LinkStats.ChannelDepth> channelDepth) {
    public ReportFacts {
      Objects.requireNonNull(reportDate, "reportDate");
    }
  }

  /** 채널 최초 관측은 기본 표본 조건을 만족할 때만 읽는다. 인앱·충성도는 각각의 표본 조건을 적용한다. */
  public List<LinkStats.Insight> computeReport(
      ReportFacts facts, Supplier<List<HostFirstSeenRow>> channelFirstSeen) {
    List<LinkStats.Insight> insights = generalInsights(facts);
    if (facts.total() >= MIN_TOTAL_FOR_INSIGHTS) {
      channelJump(channelFirstSeen.get()).ifPresent(insights::add);
    }
    inAppBrowser(facts.clientApps(), facts.human()).ifPresent(insights::add);
    channelLoyalty(facts.channelDepth()).ifPresent(insights::add);
    return insights;
  }

  /** 최초 채널보다 한 시간 이상 늦게 관측된 첫 채널을 찾는다. 입력은 최초 관측 시각 순서다. */
  public Optional<LinkStats.Insight> channelJump(List<HostFirstSeenRow> rows) {
    if (rows.size() < 2) return Optional.empty();
    HostFirstSeenRow origin = rows.get(0);
    if (origin.getHost() == null || origin.getFirstSeenEpoch() == null) return Optional.empty();
    long originEpoch = origin.getFirstSeenEpoch();
    for (int i = 1; i < rows.size(); i++) {
      HostFirstSeenRow row = rows.get(i);
      if (row.getHost() == null || row.getFirstSeenEpoch() == null) continue;
      long gapSeconds = row.getFirstSeenEpoch() - originEpoch;
      if (gapSeconds >= 3600) {
        String message = msg("insight.CHANNEL_JUMP", origin.getHost(), row.getHost());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("origin", origin.getHost());
        data.put("jumpedTo", row.getHost());
        data.put("gapHours", gapSeconds / 3600);
        return Optional.of(new LinkStats.Insight("CHANNEL_JUMP", "info", message, data));
      }
    }
    return Optional.empty();
  }

  private List<LinkStats.Insight> generalInsights(ReportFacts facts) {
    List<LinkStats.Insight> insights = new ArrayList<>();
    if (facts.total() < MIN_TOTAL_FOR_INSIGHTS) {
      return insights;
    }

    peakHour(facts.heatmap(), facts.total()).ifPresent(insights::add);
    topChannel(facts.channels(), facts.total()).ifPresent(insights::add);
    countryConcentration(facts.countries(), facts.total()).ifPresent(insights::add);
    visitorMix(facts.returnRate()).ifPresent(insights::add);
    botRatio(facts.bot(), facts.total()).ifPresent(insights::add);
    decayShape(facts.lifecycle()).ifPresent(insights::add);
    weekOverWeek(facts.dailyClicks()).ifPresent(insights::add);
    darkSocial(facts.channels(), facts.total()).ifPresent(insights::add);
    secondWind(facts.dailyClicks()).ifPresent(insights::add);
    dormancy(facts.dailyClicks(), facts.reportDate()).ifPresent(insights::add);
    return insights;
  }

  /** 링크가 게시된 곳이 아니라 열린 앱의 비중을 집계한다. */
  public Optional<LinkStats.Insight> inAppBrowser(
      List<LinkStats.ClientAppClick> clientApps, long humanClicks) {
    if (clientApps == null || clientApps.isEmpty() || humanClicks < MIN_TOTAL_FOR_INSIGHTS) {
      return Optional.empty();
    }
    long inApp = 0;
    LinkStats.ClientAppClick top = clientApps.get(0);
    for (LinkStats.ClientAppClick c : clientApps) {
      inApp += c.count();
      if (c.count() > top.count()) top = c;
    }
    double share = (double) inApp / humanClicks;
    if (share < IN_APP_SHARE_THRESHOLD) return Optional.empty();
    String message = msg("insight.IN_APP_BROWSER", appName(top.app()), pct(share));
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("topApp", top.app());
    data.put("topAppClicks", top.count());
    data.put("inAppClicks", inApp);
    data.put("share", round3(share));
    return Optional.of(new LinkStats.Insight("IN_APP_BROWSER", "info", message, data));
  }

  /** 클릭 수가 아닌 재방문율로 채널을 비교한다. */
  public Optional<LinkStats.Insight> channelLoyalty(List<LinkStats.ChannelDepth> channelDepth) {
    if (channelDepth == null || channelDepth.isEmpty()) return Optional.empty();
    LinkStats.ChannelDepth best = null;
    for (LinkStats.ChannelDepth c : channelDepth) {
      // 재방문 수 자체가 표본 하한을 대신한다 — 방문자 수는 계약에 없고, 재방문 N명이면 방문자는 최소 N명이다.
      if (c.returningVisitors() < CHANNEL_LOYALTY_MIN_VISITORS) continue;
      if (c.returnRatio() < CHANNEL_LOYALTY_THRESHOLD) continue;
      if (best == null || c.returnRatio() > best.returnRatio()) best = c;
    }
    if (best == null) return Optional.empty();
    String message = msg("insight.CHANNEL_LOYALTY", best.host(), pct(best.returnRatio()));
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("host", best.host());
    data.put("returnRatio", round3(best.returnRatio()));
    data.put("returningVisitors", best.returningVisitors());
    return Optional.of(new LinkStats.Insight("CHANNEL_LOYALTY", "info", message, data));
  }

  // 표시 이름이 없으면 저장된 값을 그대로 사용한다.
  private String appName(String app) {
    return messages.getMessage("clientApp." + app, null, app, LocaleContextHolder.getLocale());
  }

  private String msg(String code, Object... args) {
    return messages.getMessage(code, args, LocaleContextHolder.getLocale());
  }

  // 템플릿이 %를 붙이므로 로케일과 무관한 숫자 문자열만 만든다.
  private static String pct(double ratio) {
    return String.format(Locale.ROOT, "%.1f", ratio * 100);
  }

  // 알 수 없는 요일 값은 그대로 표시한다.
  private String dayName(String dow) {
    return messages.getMessage("dayOfWeek." + dow, null, dow, LocaleContextHolder.getLocale());
  }

  // referrer 없는 사람 클릭 비율이며, 실제 공유 경로는 알 수 없다.
  private Optional<LinkStats.Insight> darkSocial(
      List<LinkStats.ChannelClick> channels, long total) {
    if (total == 0) return Optional.empty();
    long direct = 0;
    for (LinkStats.ChannelClick c : channels) {
      if ("direct".equals(c.channel())) direct += c.count();
    }
    double share = (double) direct / total;
    if (share < 0.3) return Optional.empty();
    String message = msg("insight.DARK_SOCIAL", pct(share));
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("share", round3(share));
    data.put("directClicks", direct);
    return Optional.of(new LinkStats.Insight("DARK_SOCIAL", "info", message, data));
  }

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
      String message = msg("insight.SECOND_WIND", String.valueOf(recent3));
      Map<String, Object> data = new LinkedHashMap<>();
      data.put("recent3", recent3);
      data.put("dormantAvg", round3(dormantAvg));
      return Optional.of(new LinkStats.Insight("SECOND_WIND", "info", message, data));
    }
    return Optional.empty();
  }

  private Optional<LinkStats.Insight> dormancy(
      List<LinkStats.DailyClick> dailyClicks, LocalDate reportDate) {
    LocalDate lastActive = null;
    for (LinkStats.DailyClick dc : dailyClicks) {
      if (dc.count() > 0) lastActive = dc.date();
    }
    if (lastActive == null) return Optional.empty();
    long daysIdle = ChronoUnit.DAYS.between(lastActive, reportDate);
    if (daysIdle < 7) return Optional.empty();
    String message = msg("insight.DORMANT", String.valueOf(daysIdle));
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
    String message =
        msg("insight.PEAK_HOUR", dayName(dow), String.valueOf(peak.hour()), pct(share));
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
    String message = msg("insight.TOP_CHANNEL", pct(share), top.channel());
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
    String message = msg("insight.COUNTRY_CONCENTRATION", pct(share), top.country());
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
    String message = msg("insight.VISITOR_MIX", pct(newShare));
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("newShare", round3(newShare));
    data.put("returningShare", round3(1.0 - newShare));
    return Optional.of(new LinkStats.Insight("VISITOR_MIX", "info", message, data));
  }

  private Optional<LinkStats.Insight> botRatio(long bot, long total) {
    if (total == 0) return Optional.empty();
    double share = (double) bot / total;
    if (share < BOT_RATIO_THRESHOLD) return Optional.empty();
    String message = msg("insight.BOT_RATIO_HIGH", pct(share));
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
      String message = msg("insight.FAST_DECAY", pct(firstDayShare));
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
    String signedPct = (ratio > 0 ? "+" : "") + pct(ratio);
    String message = msg("insight.WEEK_OVER_WEEK", signedPct);
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
}
