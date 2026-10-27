package com.example.short_link.common.analytics;

import com.example.short_link.common.analytics.VisitProjections.BrowserVisitRow;
import com.example.short_link.common.analytics.VisitProjections.CountryVisitRow;
import com.example.short_link.common.analytics.VisitProjections.DailyVisitRow;
import com.example.short_link.common.analytics.VisitProjections.DeviceVisitRow;
import com.example.short_link.common.analytics.VisitProjections.HeatmapVisitRow;
import com.example.short_link.common.analytics.VisitProjections.HourVisitRow;
import com.example.short_link.common.analytics.VisitProjections.ReferrerHostVisitRow;
import com.example.short_link.common.analytics.VisitProjections.SourceChannelVisitRow;
import com.example.short_link.common.analytics.VisitProjections.UtmCampaignVisitRow;
import com.example.short_link.common.analytics.VisitProjections.UtmSourceVisitRow;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.function.Function;

public final class VisitStatsAssembler {

  public static final Duration DAILY_WINDOW = Duration.ofDays(30);
  public static final int TOP_SIZE = 50;

  private VisitStatsAssembler() {}

  public record Totals(
      long totalVisits,
      long humanVisits,
      long botVisits,
      long uniqueVisits,
      Instant firstVisitAt,
      Instant lastVisitAt) {}

  public record Rows(
      List<DailyVisitRow> daily,
      List<HourVisitRow> hourly,
      List<HeatmapVisitRow> heatmap,
      List<CountryVisitRow> countries,
      List<DeviceVisitRow> devices,
      List<BrowserVisitRow> browsers,
      List<ReferrerHostVisitRow> referrerHosts,
      List<SourceChannelVisitRow> sourceChannels,
      List<UtmCampaignVisitRow> utmCampaigns,
      List<UtmSourceVisitRow> utmSources) {}

  public static VisitStats assemble(ZoneId zone, Totals totals, Rows rows) {
    List<VisitStats.HourVisit> hourly =
        map(rows.hourly(), r -> new VisitStats.HourVisit(r.getHour(), r.getCount()));
    return new VisitStats(
        zone.getId(),
        totals.totalVisits(),
        totals.humanVisits(),
        totals.botVisits(),
        totals.uniqueVisits(),
        totals.firstVisitAt(),
        totals.lastVisitAt(),
        peakHour(hourly),
        map(rows.daily(), r -> new VisitStats.DailyVisit(r.getDay(), r.getCount())),
        hourly,
        map(
            rows.heatmap(),
            r -> new HeatmapCell(dayOfWeekName(r.getDow()), r.getHour(), r.getCount())),
        map(
            rows.countries(),
            r -> new VisitStats.CountryVisit(orUnknown(r.getCountry()), r.getCount())),
        map(
            rows.devices(),
            r -> new VisitStats.DeviceVisit(orUnknown(r.getDevice()), r.getCount())),
        map(
            rows.browsers(),
            r -> new VisitStats.BrowserVisit(orUnknown(r.getBrowser()), r.getCount())),
        map(rows.referrerHosts(), r -> new VisitStats.ReferrerHostVisit(r.getHost(), r.getCount())),
        map(
            rows.sourceChannels(),
            r -> new VisitStats.SourceChannelVisit(r.getSource(), r.getCount())),
        map(
            rows.utmCampaigns(),
            r -> new VisitStats.UtmCampaignVisit(r.getCampaign(), r.getCount())),
        map(rows.utmSources(), r -> new VisitStats.UtmSourceVisit(r.getSource(), r.getCount())));
  }

  private static Integer peakHour(List<VisitStats.HourVisit> hourly) {
    return hourly.stream()
        .max((a, b) -> Long.compare(a.count(), b.count()))
        .map(VisitStats.HourVisit::hour)
        .orElse(null);
  }

  private static String dayOfWeekName(int dow) {
    return switch (dow) {
      case 1 -> DayOfWeek.SUNDAY.name();
      case 2 -> DayOfWeek.MONDAY.name();
      case 3 -> DayOfWeek.TUESDAY.name();
      case 4 -> DayOfWeek.WEDNESDAY.name();
      case 5 -> DayOfWeek.THURSDAY.name();
      case 6 -> DayOfWeek.FRIDAY.name();
      case 7 -> DayOfWeek.SATURDAY.name();
      default -> "UNKNOWN";
    };
  }

  private static String orUnknown(String value) {
    return value == null ? "unknown" : value;
  }

  private static <R, V> List<V> map(List<R> rows, Function<R, V> toVisit) {
    return rows.stream().map(toVisit).toList();
  }
}
