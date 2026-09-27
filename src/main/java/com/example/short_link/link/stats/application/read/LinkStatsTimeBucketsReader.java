package com.example.short_link.link.stats.application.read;

import com.example.short_link.link.application.dto.LinkStats;
import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.stats.domain.repository.ClickTimeReadRepository;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.DailyClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.DayOfWeekClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.HeatmapRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.HourClickRow;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class LinkStatsTimeBucketsReader {

  private static final Duration DAILY_WINDOW = Duration.ofDays(30);

  private final ClickTimeReadRepository clickTime;

  TimeBuckets read(LinkId linkId, Instant linkCreatedAt, ZoneId zone, Instant reportTime) {
    Long id = linkId.value();
    Instant until = reportTime.truncatedTo(ChronoUnit.SECONDS).plusSeconds(1);
    List<OffsetPeriod> recent = OffsetPeriod.between(zone, reportTime.minus(DAILY_WINDOW), until);
    List<OffsetPeriod> lifetime = OffsetPeriod.between(zone, linkCreatedAt, until);

    List<LinkStats.DailyClick> daily =
        sum(
                recent,
                p -> clickTime.findDailyClicks(id, p.from(), p.until(), p.offset()),
                DailyClickRow::getDay,
                DailyClickRow::getCount)
            .entrySet()
            .stream()
            .sorted(Map.Entry.comparingByKey())
            .map(e -> new LinkStats.DailyClick(e.getKey(), e.getValue()))
            .toList();
    List<LinkStats.HourClick> hourly =
        sum(
                lifetime,
                p -> clickTime.findHourlyClicks(id, p.from(), p.until(), p.offset()),
                HourClickRow::getHour,
                HourClickRow::getCount)
            .entrySet()
            .stream()
            .sorted(Map.Entry.comparingByKey())
            .map(e -> new LinkStats.HourClick(e.getKey(), e.getValue()))
            .toList();
    Integer peakHour =
        hourly.stream()
            .max((a, b) -> Long.compare(a.count(), b.count()))
            .map(LinkStats.HourClick::hour)
            .orElse(null);
    List<LinkStats.DayOfWeekClick> dayOfWeek =
        sum(
                lifetime,
                p -> clickTime.findDayOfWeekClicks(id, p.from(), p.until(), p.offset()),
                DayOfWeekClickRow::getDow,
                DayOfWeekClickRow::getCount)
            .entrySet()
            .stream()
            .sorted(Map.Entry.comparingByKey())
            .map(e -> new LinkStats.DayOfWeekClick(mapDayOfWeek(e.getKey()), e.getValue()))
            .toList();
    List<LinkStats.HeatmapCell> heatmap =
        sum(
                lifetime,
                p -> clickTime.findHeatmap(id, p.from(), p.until(), p.offset()),
                r -> new DowHour(r.getDow(), r.getHour()),
                HeatmapRow::getCount)
            .entrySet()
            .stream()
            .sorted(Map.Entry.comparingByKey(DowHour.ORDER))
            .map(
                e ->
                    new LinkStats.HeatmapCell(
                        mapDayOfWeek(e.getKey().dow()), e.getKey().hour(), e.getValue()))
            .toList();
    return new TimeBuckets(daily, hourly, peakHour, dayOfWeek, heatmap);
  }

  private static <R, K> Map<K, Long> sum(
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

  private record DowHour(int dow, int hour) {
    static final Comparator<DowHour> ORDER =
        Comparator.comparingInt(DowHour::dow).thenComparingInt(DowHour::hour);
  }

  static String mapDayOfWeek(int mysqlDow) {
    return switch (mysqlDow) {
      case 1 -> DayOfWeek.SUNDAY.toString();
      case 2 -> DayOfWeek.MONDAY.toString();
      case 3 -> DayOfWeek.TUESDAY.toString();
      case 4 -> DayOfWeek.WEDNESDAY.toString();
      case 5 -> DayOfWeek.THURSDAY.toString();
      case 6 -> DayOfWeek.FRIDAY.toString();
      case 7 -> DayOfWeek.SATURDAY.toString();
      default -> "UNKNOWN";
    };
  }

  record TimeBuckets(
      List<LinkStats.DailyClick> daily,
      List<LinkStats.HourClick> hourly,
      Integer peakHour,
      List<LinkStats.DayOfWeekClick> dayOfWeek,
      List<LinkStats.HeatmapCell> heatmap) {}
}
