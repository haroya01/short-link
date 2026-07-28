package com.example.short_link.link.stats.application;

import com.example.short_link.link.application.dto.LinkStats;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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

  /** 인앱 브라우저 비중이 이 정도는 돼야 "어디서 열렸는지"가 행동을 바꿀 만한 사실이 된다. */
  private static final double IN_APP_SHARE_THRESHOLD = 0.2;

  /** 채널 재방문율은 이 위여야 눈에 띈다 — 링크 전체 평균 재방문율은 보통 이보다 한참 낮다. */
  private static final double CHANNEL_LOYALTY_THRESHOLD = 0.3;

  /** 방문자 표본이 이보다 적으면 재방문율 한두 명에 비율이 요동쳐 신호가 아니라 잡음이다. */
  private static final long CHANNEL_LOYALTY_MIN_VISITORS = 5;

  private final MessageSource messages;

  public LinkInsights(MessageSource messages) {
    this.messages = messages;
  }

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

  /**
   * 인앱 브라우저 비중 — "카카오톡에서 열린 게 N%". 링크가 어디에 붙어 있는지가 아니라 어디에서 *열렸는지*라, 랜딩을 앱 웹뷰에서도 멀쩡히 돌게 만들지 말지를
   * 가른다(로그인 리다이렉트·폰트·다운로드가 인앱에서 곧잘 깨진다).
   *
   * <p>{@link #compute} 밖에 있는 건 {@code channelJump} 와 같은 이유다 — 조립기가 이미 들고 있는 데이터로 만드는 규칙이라 기존 호출자
   * 계약을 건드리지 않는다.
   */
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

  /**
   * 채널 충성도 — "○○에서 온 사람의 N%가 다시 왔어요". 클릭 수 1등이 아니라 *재방문율* 1등을 집는다. 한 번 터지고 끝난 채널과 사람을 남긴 채널은 다른
   * 이야기고, 다음에 어디에 글을 올릴지를 정하는 건 후자다.
   */
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

  /// 인앱 브라우저 표시 이름 — 없으면 저장된 값 그대로(요일 폴백과 같은 방식).
  private String appName(String app) {
    return messages.getMessage("clientApp." + app, null, app, LocaleContextHolder.getLocale());
  }

  /// 메시지는 요청 로케일(Accept-Language)로 — messages_xx.properties 에서 코드로 룩업.
  private String msg(String code, Object... args) {
    return messages.getMessage(code, args, LocaleContextHolder.getLocale());
  }

  /// 퍼센트 숫자를 미리 문자열로(로케일 무관). 템플릿의 % 는 리터럴이라 여기선 숫자만.
  private static String pct(double ratio) {
    return String.format(Locale.ROOT, "%.1f", ratio * 100);
  }

  /// 알 수 없는 요일 enum 이면 그 값 그대로(예외 없이) — 기존 dayOfWeekKo 폴백과 동일.
  private String dayName(String dow) {
    return messages.getMessage("dayOfWeek." + dow, null, dow, LocaleContextHolder.getLocale());
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
    String message = msg("insight.DARK_SOCIAL", pct(share));
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
      String message = msg("insight.SECOND_WIND", String.valueOf(recent3));
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
