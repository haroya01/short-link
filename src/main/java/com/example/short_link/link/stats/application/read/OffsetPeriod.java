package com.example.short_link.link.stats.application.read;

import java.time.Instant;
import java.time.ZoneId;
import java.time.zone.ZoneOffsetTransition;
import java.util.ArrayList;
import java.util.List;

record OffsetPeriod(Instant from, Instant until, String offset) {

  static List<OffsetPeriod> between(ZoneId zone, Instant from, Instant until) {
    List<OffsetPeriod> periods = new ArrayList<>();
    Instant start = from;
    ZoneOffsetTransition next = zone.getRules().nextTransition(start);
    while (next != null && next.getInstant().isBefore(until)) {
      periods.add(new OffsetPeriod(start, next.getInstant(), offsetAt(zone, start)));
      start = next.getInstant();
      next = zone.getRules().nextTransition(start);
    }
    periods.add(new OffsetPeriod(start, until, offsetAt(zone, start)));
    return periods;
  }

  private static String offsetAt(ZoneId zone, Instant instant) {
    return LinkStatsDateSupport.offsetAt(zone, instant);
  }
}
