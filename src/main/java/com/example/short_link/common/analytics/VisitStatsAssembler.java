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
import com.example.short_link.common.time.OffsetPeriod;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
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
      Instant lastVisitAt) {

    public Instant firstVisitOr(Instant fallback) {
      return firstVisitAt == null ? fallback : firstVisitAt;
    }
  }

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

  public static <R> List<R> overPeriods(
      List<OffsetPeriod> periods, Function<OffsetPeriod, List<R>> query) {
    return periods.stream().flatMap(period -> query.apply(period).stream()).toList();
  }

  public static VisitStats assemble(ZoneId zone, Totals totals, Rows rows) {
    List<VisitStats.HourVisit> hourly =
        sums(
                rows.hourly(),
                HourVisitRow::getHour,
                HourVisitRow::getCount,
                Comparator.naturalOrder())
            .entrySet()
            .stream()
            .map(e -> new VisitStats.HourVisit(e.getKey(), e.getValue()))
            .toList();
    return new VisitStats(
        zone.getId(),
        totals.totalVisits(),
        totals.humanVisits(),
        totals.botVisits(),
        totals.uniqueVisits(),
        totals.firstVisitAt(),
        totals.lastVisitAt(),
        peakHour(hourly),
        sums(
                rows.daily(),
                DailyVisitRow::getDay,
                DailyVisitRow::getCount,
                Comparator.naturalOrder())
            .entrySet()
            .stream()
            .map(e -> new VisitStats.DailyVisit(e.getKey(), e.getValue()))
            .toList(),
        hourly,
        sums(
                rows.heatmap(),
                r -> new DowHour(r.getDow(), r.getHour()),
                HeatmapVisitRow::getCount,
                DowHour.ORDER)
            .entrySet()
            .stream()
            .map(
                e ->
                    new HeatmapCell(
                        dayOfWeekName(e.getKey().dow()), e.getKey().hour(), e.getValue()))
            .toList(),
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

  private static <R, K> SortedMap<K, Long> sums(
      List<R> rows, Function<R, K> key, Function<R, Long> count, Comparator<K> order) {
    SortedMap<K, Long> sums = new TreeMap<>(order);
    for (R row : rows) {
      sums.merge(key.apply(row), count.apply(row), Long::sum);
    }
    return sums;
  }

  private record DowHour(int dow, int hour) {
    static final Comparator<DowHour> ORDER =
        Comparator.comparingInt(DowHour::dow).thenComparingInt(DowHour::hour);
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
