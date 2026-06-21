package com.example.short_link.link.stats.application.read;

import com.example.short_link.link.application.dto.LinkStats;
import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.stats.domain.repository.ClickLifecycleReadRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class LinkStatsLifecycleReader {

  private static final int LIFECYCLE_MAX_DAY = 30;

  private final ClickLifecycleReadRepository clickLifecycle;

  LinkStats.ReturnRate returnRate(LinkId linkId) {
    var row = clickLifecycle.findReturnRate(linkId.value());
    long newCount = row == null || row.getNewCount() == null ? 0 : row.getNewCount();
    long returningCount =
        row == null || row.getReturningCount() == null ? 0 : row.getReturningCount();
    long total = newCount + returningCount;
    double ratio = total == 0 ? 0.0 : (double) returningCount / total;
    return new LinkStats.ReturnRate(newCount, returningCount, ratio);
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
            String.format(
                java.util.Locale.KOREAN,
                "%s에서 시작했는데 %s로 퍼졌어요 — 원래 청중 밖으로 넘어갔어요",
                origin.getHost(),
                row.getHost());
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
