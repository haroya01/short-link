package com.example.short_link.common.time;

import java.time.Instant;
import java.time.ZoneId;
import java.time.zone.ZoneOffsetTransition;
import java.util.ArrayList;
import java.util.List;

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
}
