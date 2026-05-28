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
