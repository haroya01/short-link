package com.example.short_link.common.time;

import java.time.Instant;
import java.time.ZoneId;
import java.time.zone.ZoneOffsetTransition;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public record OffsetPeriod(Instant from, Instant until, String offset) {

  public static List<OffsetPeriod> between(ZoneId zone, Instant from, Instant until) {
    List<OffsetPeriod> periods = new ArrayList<>();
    Instant start = from;
    ZoneOffsetTransition next = zone.getRules().nextTransition(start);
    while (next != null && next.getInstant().isBefore(until)) {
      periods.add(new OffsetPeriod(start, next.getInstant(), ReportZones.sqlOffset(zone, start)));
      start = next.getInstant();
      next = zone.getRules().nextTransition(start);
    }
    periods.add(new OffsetPeriod(start, until, ReportZones.sqlOffset(zone, start)));
    return periods;
  }

  public static <R, K> Map<K, Long> sum(
      List<OffsetPeriod> periods,
      Function<OffsetPeriod, List<R>> query,
      Function<R, K> key,
      Function<R, Long> count) {
    Map<K, Long> sums = new HashMap<>();
    for (OffsetPeriod period : periods) {
      for (R row : query.apply(period)) {
        sums.merge(key.apply(row), count.apply(row), Long::sum);
      }
    }
    return sums;
  }
}
