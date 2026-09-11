package com.example.short_link.link.stats.infrastructure.persistence;

import com.example.short_link.link.stats.domain.repository.ClickTimeReadRepository;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.DailyClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.DailyClicksByLinkRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.DayOfWeekClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.HeatmapRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.HourClickRow;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
    // Instant를 숫자로 전달하고 UTC 날짜로 복원해 JDBC/DB 세션의 달력 변환을 거치지 않는다.
    BigDecimal fromEpoch =
        BigDecimal.valueOf(from.getEpochSecond()).add(BigDecimal.valueOf(from.getNano(), 9));
    return jpa.findUtcDailyClicksByLinkIdsSince(ids, fromEpoch).stream()
        .<DailyClicksByLinkRow>map(
            row ->
                new UtcDailyClicks(
                    row.getLinkId(), LocalDate.ofEpochDay(row.getEpochDay()), row.getCount()))
        .toList();
  }

  private record UtcDailyClicks(Long linkId, LocalDate day, Long count)
      implements DailyClicksByLinkRow {
    @Override
    public Long getLinkId() {
      return linkId;
    }

    @Override
    public LocalDate getDay() {
      return day;
    }

    @Override
    public Long getCount() {
      return count;
    }
  }
}
