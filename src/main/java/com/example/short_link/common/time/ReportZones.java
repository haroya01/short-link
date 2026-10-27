package com.example.short_link.common.time;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

public final class ReportZones {

  public static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Seoul");

  private ReportZones() {}

  public static ZoneId zoneOf(String timezone) {
    if (timezone == null || timezone.isBlank()) return DEFAULT_ZONE;
    try {
      return ZoneId.of(timezone);
    } catch (DateTimeException e) {
      return DEFAULT_ZONE;
    }
  }

  public static String sqlOffset(ZoneId zone, Instant at) {
    ZoneOffset offset = zone.getRules().getOffset(at);
    return offset.getId().equals("Z") ? "+00:00" : offset.getId();
  }
}
