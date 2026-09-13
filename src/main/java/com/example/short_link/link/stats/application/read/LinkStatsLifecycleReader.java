package com.example.short_link.link.stats.application.read;

import com.example.short_link.link.application.dto.LinkStats;
import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.stats.domain.repository.ClickLifecycleReadRepository;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.HostFirstSeenRow;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class LinkStatsLifecycleReader {

  private static final int LIFECYCLE_MAX_DAY = 30;

  private static final int CHANNEL_DEPTH_TOP = 10;

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

  /** 재방문은 링크 전체와 같은 30분 세션화 정의를 사용한다. */
  List<LinkStats.ChannelDepth> channelDepth(LinkId linkId) {
    return clickLifecycle.findChannelDepth(linkId.value(), CHANNEL_DEPTH_TOP).stream()
        .map(
            r -> {
              long visitors = r.getVisitors() == null ? 0 : r.getVisitors();
              long returning = r.getReturningVisitors() == null ? 0 : r.getReturningVisitors();
              // GPC 방문자는 visitor_hash가 없어 채널 재방문율의 분자와 분모 모두에서 빠진다.
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

  List<HostFirstSeenRow> channelFirstSeen(LinkId linkId) {
    return clickLifecycle.findFirstSeenByReferrerHost(linkId.value());
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
