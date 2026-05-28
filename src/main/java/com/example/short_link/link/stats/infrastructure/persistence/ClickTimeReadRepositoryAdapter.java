package com.example.short_link.link.stats.infrastructure.persistence;

import com.example.short_link.link.stats.domain.repository.ClickTimeReadRepository;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.DailyClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.DailyClicksByLinkRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.DayOfWeekClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.HeatmapRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.HourClickRow;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class ClickTimeReadRepositoryAdapter implements ClickTimeReadRepository {

  private final JpaClickTimeReadRepository jpa;

  @Override
  public List<DailyClickRow> findDailyClicks(Long linkId, Instant from, String timezone) {
    return jpa.findDailyClicks(linkId, from, timezone);
  }

  @Override
  public List<HourClickRow> findHourlyClicks(Long linkId, String timezone) {
    return jpa.findHourlyClicks(linkId, timezone);
  }

  @Override
  public List<DayOfWeekClickRow> findDayOfWeekClicks(Long linkId, String timezone) {
    return jpa.findDayOfWeekClicks(linkId, timezone);
  }

  @Override
  public List<HeatmapRow> findHeatmap(Long linkId, String timezone) {
    return jpa.findHeatmap(linkId, timezone);
  }

  @Override
  public List<HourClickRow> findHourlyClicksByLinkIdsSince(
      List<Long> linkIds, Instant since, String timezone) {
    return jpa.findHourlyClicksByLinkIdsSince(linkIds, since, timezone);
  }

  @Override
  public List<DailyClickRow> findDailyClicksByLinkIdsSince(
      List<Long> linkIds, Instant since, String timezone) {
    return jpa.findDailyClicksByLinkIdsSince(linkIds, since, timezone);
  }

  @Override
  public List<HeatmapRow> findHeatmapByLinkIdsSince(
      List<Long> linkIds, Instant since, String timezone) {
    return jpa.findHeatmapByLinkIdsSince(linkIds, since, timezone);
  }

  @Override
  public List<DailyClicksByLinkRow> findDailyClicksByLinkIdsSince(List<Long> ids, Instant from) {
    return jpa.findDailyClicksByLinkIdsSince(ids, from);
  }
}
