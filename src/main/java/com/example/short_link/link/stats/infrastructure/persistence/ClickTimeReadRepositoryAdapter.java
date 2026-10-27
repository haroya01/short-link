package com.example.short_link.link.stats.infrastructure.persistence;

import com.example.short_link.link.stats.application.read.ClickTimeReader;
import com.example.short_link.link.stats.domain.projection.ClickProjections.DailyClickBucketRow;
import com.example.short_link.link.stats.domain.projection.ClickProjections.DailyClickRow;
import com.example.short_link.link.stats.domain.projection.ClickProjections.DayOfWeekClickRow;
import com.example.short_link.link.stats.domain.projection.ClickProjections.HeatmapRow;
import com.example.short_link.link.stats.domain.projection.ClickProjections.HourClickRow;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class ClickTimeReadRepositoryAdapter implements ClickTimeReader {

  private final JpaClickTimeReadRepository jpa;

  @Override
  public List<DailyClickBucketRow> findDailyClickBucketsByLinkIds(
      List<Long> ids, List<Instant> dayStarts, Instant until) {
    if (ids.isEmpty()) return List.of();
    if (dayStarts.size() != 7) throw new IllegalArgumentException("seven day starts required");
    List<BigDecimal> b = dayStarts.stream().map(ClickTimeReadRepositoryAdapter::epoch).toList();
    return jpa.findDailyClickBucketsByLinkIds(
        ids, b.get(0), b.get(1), b.get(2), b.get(3), b.get(4), b.get(5), b.get(6), epoch(until));
  }

  private static BigDecimal epoch(Instant instant) {
    return BigDecimal.valueOf(instant.getEpochSecond())
        .add(BigDecimal.valueOf(instant.getNano(), 9));
  }

  @Override
  public List<DailyClickRow> findDailyClicks(
      Long linkId, Instant from, Instant until, String timezone) {
    return jpa.findDailyClicks(linkId, from, until, timezone);
  }

  @Override
  public List<HourClickRow> findHourlyClicks(
      Long linkId, Instant from, Instant until, String timezone) {
    return jpa.findHourlyClicks(linkId, epoch(from), epoch(until), timezone);
  }

  @Override
  public List<DayOfWeekClickRow> findDayOfWeekClicks(
      Long linkId, Instant from, Instant until, String timezone) {
    return jpa.findDayOfWeekClicks(linkId, epoch(from), epoch(until), timezone);
  }

  @Override
  public List<HeatmapRow> findHeatmap(Long linkId, Instant from, Instant until, String timezone) {
    return jpa.findHeatmap(linkId, epoch(from), epoch(until), timezone);
  }

  @Override
  public List<HourClickRow> findHourlyClicksByLinkIdsSince(
      List<Long> linkIds, Instant since, String timezone) {
    return jpa.findHourlyClicksByLinkIdsSince(linkIds, epoch(since), timezone);
  }

  @Override
  public List<DailyClickRow> findDailyClicksByLinkIdsSince(
      List<Long> linkIds, Instant since, String timezone) {
    return jpa.findDailyClicksByLinkIdsSince(linkIds, epoch(since), timezone);
  }

  @Override
  public List<HeatmapRow> findHeatmapByLinkIdsSince(
      List<Long> linkIds, Instant since, String timezone) {
    return jpa.findHeatmapByLinkIdsSince(linkIds, epoch(since), timezone);
  }
}
