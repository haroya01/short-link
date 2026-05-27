package com.example.short_link.link.stats.application.read;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

final class LinkStatsDateSupport {

  static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Seoul");

  private LinkStatsDateSupport() {}

  static ZoneId safeZone(String tz) {
    if (tz == null || tz.isBlank()) return DEFAULT_ZONE;
    try {
      return ZoneId.of(tz);
    } catch (DateTimeException e) {
      return DEFAULT_ZONE;
    }
  }

  static String currentOffset(ZoneId zone) {
    ZoneOffset offset = zone.getRules().getOffset(Instant.now());
    return offset.getId().equals("Z") ? "+00:00" : offset.getId();
  }
}
