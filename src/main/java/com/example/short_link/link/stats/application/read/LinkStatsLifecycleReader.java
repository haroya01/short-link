package com.example.short_link.link.stats.application.read;

import com.example.short_link.link.application.dto.LinkStats;
import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.stats.domain.repository.ClickLifecycleReadRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class LinkStatsLifecycleReader {

  private static final int LIFECYCLE_MAX_DAY = 30;

  /**
   * 채널 깊이는 상위 10개만. 이 블록은 채널을 "훑어보는" 게 아니라 "읽는" 화면이라 — 채널마다 첫 등장 시각과 재방문율까지 딸려 나온다 — 꼬리까지 50개를 실어
   * 보내면 응답만 무거워지고 아무도 안 본다. 전체 목록이 필요하면 referrerHostClicks(상위 50)가 이미 있다.
   */
  private static final int CHANNEL_DEPTH_TOP = 10;

  private final ClickLifecycleReadRepository clickLifecycle;
  private final MessageSource messages;

  LinkStats.ReturnRate returnRate(LinkId linkId) {
    var row = clickLifecycle.findReturnRate(linkId.value());
    long newCount = row == null || row.getNewCount() == null ? 0 : row.getNewCount();
    long returningCount =
        row == null || row.getReturningCount() == null ? 0 : row.getReturningCount();
    long total = newCount + returningCount;
    double ratio = total == 0 ? 0.0 : (double) returningCount / total;
    return new LinkStats.ReturnRate(newCount, returningCount, ratio);
  }

  /**
   * 채널별 깊이 — 클릭 수만 보면 "인스타 40, 노션 40"이 같은 채널로 보이지만, 첫 등장 시각과 재방문율을 함께 두면 "인스타는 네 시간 안에 다 타고 끝났고 노션은
   * 사흘째 꾸준하다"가 드러난다. 재방문 정의는 링크 전체 재방문율과 같은 30분 세션화(레포지토리 주석 참고).
   */
  List<LinkStats.ChannelDepth> channelDepth(LinkId linkId) {
    return clickLifecycle.findChannelDepth(linkId.value(), CHANNEL_DEPTH_TOP).stream()
        .map(
            r -> {
              long visitors = r.getVisitors() == null ? 0 : r.getVisitors();
              long returning = r.getReturningVisitors() == null ? 0 : r.getReturningVisitors();
              // 분모는 그 채널의 (식별 가능한) 방문자 수다. GPC 옵트아웃 방문자는 visitor_hash 가 없어
              // 분자·분모 어디에도 안 들어간다 — 비율을 부풀리지도 낮추지도 않는다.
              double ratio = visitors == 0 ? 0.0 : (double) returning / visitors;
              return new LinkStats.ChannelDepth(
                  r.getHost(),
                  r.getCount() == null ? 0 : r.getCount(),
                  r.getFirstSeenEpoch() == null
                      ? null
                      : java.time.Instant.ofEpochSecond(r.getFirstSeenEpoch()),
                  returning,
                  Math.round(ratio * 1000.0) / 1000.0);
            })
        .toList();
  }

  /// 채널 점프 — 원래(가장 이른) referrer host 이후 *1시간 이상 늦게* 처음 등장한 다른 host =
  /// 링크가 원래 청중 밖으로 넘어간 순간("발견됨"). 시계열 first-seen 으로만 derivable, 신원 X.
  java.util.Optional<LinkStats.Insight> channelJump(LinkId linkId) {
    var rows = clickLifecycle.findFirstSeenByReferrerHost(linkId.value());
    if (rows.size() < 2) return java.util.Optional.empty();
    var origin = rows.get(0);
    if (origin.getHost() == null || origin.getFirstSeenEpoch() == null)
      return java.util.Optional.empty();
    long originEpoch = origin.getFirstSeenEpoch();
    for (int i = 1; i < rows.size(); i++) {
      var row = rows.get(i);
      if (row.getHost() == null || row.getFirstSeenEpoch() == null) continue;
      long gapSeconds = row.getFirstSeenEpoch() - originEpoch;
      if (gapSeconds >= 3600) {
        String message =
            messages.getMessage(
                "insight.CHANNEL_JUMP",
                new Object[] {origin.getHost(), row.getHost()},
                LocaleContextHolder.getLocale());
        java.util.Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("origin", origin.getHost());
        data.put("jumpedTo", row.getHost());
        data.put("gapHours", gapSeconds / 3600);
        return java.util.Optional.of(new LinkStats.Insight("CHANNEL_JUMP", "info", message, data));
      }
    }
    return java.util.Optional.empty();
  }

  LinkStats.Lifecycle lifecycle(LinkId linkId) {
    List<LinkStats.DayClick> days =
        clickLifecycle.findLifecycleClicks(linkId.value(), LIFECYCLE_MAX_DAY).stream()
            .map(r -> new LinkStats.DayClick(r.getDay(), r.getCount()))
            .toList();
    Integer halfLife = halfLife(days);
    return new LinkStats.Lifecycle(days, halfLife);
  }

  static Integer halfLife(List<LinkStats.DayClick> days) {
    if (days.isEmpty()) return null;
    long total = 0;
    for (LinkStats.DayClick dc : days) total += dc.count();
    if (total == 0) return null;
    long target = (long) Math.ceil(total / 2.0);
    long cumulative = 0;
    for (LinkStats.DayClick dc : days) {
      cumulative += dc.count();
      if (cumulative >= target) return dc.day();
    }
    return null;
  }
}
