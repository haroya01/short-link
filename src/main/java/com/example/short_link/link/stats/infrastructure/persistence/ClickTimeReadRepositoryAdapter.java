package com.example.short_link.link.stats.infrastructure.persistence;

import com.example.short_link.link.stats.domain.repository.ClickTimeReadRepository;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.DailyClickBucketRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.DailyClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.DayOfWeekClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.HeatmapRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.HourClickRow;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class ClickTimeReadRepositoryAdapter implements ClickTimeReadRepository {

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
}
