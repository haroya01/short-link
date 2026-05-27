package com.example.short_link.link.stats.domain.repository;

import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.DailyClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.DailyClicksByLinkRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.DayOfWeekClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.HeatmapRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.HourClickRow;
import java.time.Instant;
import java.util.List;

public interface ClickTimeReadRepository {

  List<DailyClickRow> findDailyClicks(Long linkId, Instant from, String timezone);

  List<HourClickRow> findHourlyClicks(Long linkId, String timezone);

  List<DayOfWeekClickRow> findDayOfWeekClicks(Long linkId, String timezone);

  List<HeatmapRow> findHeatmap(Long linkId, String timezone);

  List<HourClickRow> findHourlyClicksByLinkIdsSince(
      List<Long> linkIds, Instant since, String timezone);

  List<DailyClickRow> findDailyClicksByLinkIdsSince(
      List<Long> linkIds, Instant since, String timezone);

  List<HeatmapRow> findHeatmapByLinkIdsSince(List<Long> linkIds, Instant since, String timezone);

  List<DailyClicksByLinkRow> findDailyClicksByLinkIdsSince(List<Long> ids, Instant from);
}
