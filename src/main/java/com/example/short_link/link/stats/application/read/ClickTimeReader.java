package com.example.short_link.link.stats.application.read;

import com.example.short_link.link.stats.domain.projection.ClickProjections.DailyClickBucketRow;
import com.example.short_link.link.stats.domain.projection.ClickProjections.DailyClickRow;
import com.example.short_link.link.stats.domain.projection.ClickProjections.HeatmapRow;
import com.example.short_link.link.stats.domain.projection.ClickProjections.HourClickRow;
import java.time.Instant;
import java.util.List;

public interface ClickTimeReader {

  List<DailyClickBucketRow> findDailyClickBucketsByLinkIds(
      List<Long> ids, List<Instant> dayStarts, Instant until);

  List<DailyClickRow> findDailyClicks(Long linkId, Instant from, Instant until, String timezone);

  List<HeatmapRow> findHeatmap(Long linkId, Instant from, Instant until, String timezone);

  List<HourClickRow> findHourlyClicksByLinkIds(
      List<Long> linkIds, Instant from, Instant until, String timezone);

  List<DailyClickRow> findDailyClicksByLinkIds(
      List<Long> linkIds, Instant from, Instant until, String timezone);

  List<HeatmapRow> findHeatmapByLinkIds(
      List<Long> linkIds, Instant from, Instant until, String timezone);
}
