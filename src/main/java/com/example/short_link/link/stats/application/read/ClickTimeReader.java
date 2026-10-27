package com.example.short_link.link.stats.application.read;

import com.example.short_link.link.stats.domain.projection.ClickProjections.DailyClickBucketRow;
import com.example.short_link.link.stats.domain.projection.ClickProjections.DailyClickRow;
import com.example.short_link.link.stats.domain.projection.ClickProjections.DayOfWeekClickRow;
import com.example.short_link.link.stats.domain.projection.ClickProjections.HeatmapRow;
import com.example.short_link.link.stats.domain.projection.ClickProjections.HourClickRow;
import java.time.Instant;
import java.util.List;

public interface ClickTimeReader {

  List<DailyClickBucketRow> findDailyClickBucketsByLinkIds(
      List<Long> ids, List<Instant> dayStarts, Instant until);

  List<DailyClickRow> findDailyClicks(Long linkId, Instant from, Instant until, String timezone);

  List<HourClickRow> findHourlyClicks(Long linkId, Instant from, Instant until, String timezone);

  List<DayOfWeekClickRow> findDayOfWeekClicks(
      Long linkId, Instant from, Instant until, String timezone);

  List<HeatmapRow> findHeatmap(Long linkId, Instant from, Instant until, String timezone);

  List<HourClickRow> findHourlyClicksByLinkIdsSince(
      List<Long> linkIds, Instant since, String timezone);

  List<DailyClickRow> findDailyClicksByLinkIdsSince(
      List<Long> linkIds, Instant since, String timezone);

  List<HeatmapRow> findHeatmapByLinkIdsSince(List<Long> linkIds, Instant since, String timezone);
}
