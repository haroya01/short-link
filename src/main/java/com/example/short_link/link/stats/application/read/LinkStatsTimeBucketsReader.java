package com.example.short_link.link.stats.application.read;

import com.example.short_link.link.application.dto.LinkStats;
import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.stats.domain.repository.ClickTimeReadRepository;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class LinkStatsTimeBucketsReader {

  private static final Duration DAILY_WINDOW = Duration.ofDays(30);

  private final ClickTimeReadRepository clickTime;

  TimeBuckets read(LinkId linkId, String reportTz) {
    List<LinkStats.DailyClick> daily =
        clickTime
            .findDailyClicks(linkId.value(), Instant.now().minus(DAILY_WINDOW), reportTz)
            .stream()
            .map(r -> new LinkStats.DailyClick(r.getDay(), r.getCount()))
            .toList();
    List<LinkStats.HourClick> hourly =
        clickTime.findHourlyClicks(linkId.value(), reportTz).stream()
            .map(r -> new LinkStats.HourClick(r.getHour(), r.getCount()))
            .toList();
    Integer peakHour =
        hourly.stream()
            .max((a, b) -> Long.compare(a.count(), b.count()))
            .map(LinkStats.HourClick::hour)
            .orElse(null);
    List<LinkStats.DayOfWeekClick> dayOfWeek =
        clickTime.findDayOfWeekClicks(linkId.value(), reportTz).stream()
            .map(r -> new LinkStats.DayOfWeekClick(mapDayOfWeek(r.getDow()), r.getCount()))
            .toList();
    List<LinkStats.HeatmapCell> heatmap =
        clickTime.findHeatmap(linkId.value(), reportTz).stream()
            .map(
                r -> new LinkStats.HeatmapCell(mapDayOfWeek(r.getDow()), r.getHour(), r.getCount()))
            .toList();
    return new TimeBuckets(daily, hourly, peakHour, dayOfWeek, heatmap);
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
