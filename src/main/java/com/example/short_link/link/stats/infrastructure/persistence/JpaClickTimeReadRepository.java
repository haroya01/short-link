package com.example.short_link.link.stats.infrastructure.persistence;

import com.example.short_link.link.stats.domain.ClickEventEntity;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.DailyClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.DayOfWeekClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.HeatmapRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.HourClickRow;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface JpaClickTimeReadRepository extends Repository<ClickEventEntity, Long> {

  @Query(
      "SELECT FUNCTION('DATE', FUNCTION('CONVERT_TZ', c.clickedAt, '+00:00', :tz)) AS day, "
          + "COUNT(c) AS count "
          + "FROM ClickEventEntity c WHERE c.linkId = :linkId AND c.bot = false "
          + "AND c.clickedAt >= :from "
          + "GROUP BY FUNCTION('DATE', FUNCTION('CONVERT_TZ', c.clickedAt, '+00:00', :tz)) "
          + "ORDER BY day")
  List<DailyClickRow> findDailyClicks(
      @Param("linkId") Long linkId, @Param("from") Instant from, @Param("tz") String timezone);

  @Query(
      "SELECT FUNCTION('HOUR', FUNCTION('CONVERT_TZ', c.clickedAt, '+00:00', :tz)) AS hour, "
          + "COUNT(c) AS count "
          + "FROM ClickEventEntity c WHERE c.linkId = :linkId AND c.bot = false "
          + "GROUP BY FUNCTION('HOUR', FUNCTION('CONVERT_TZ', c.clickedAt, '+00:00', :tz)) "
          + "ORDER BY hour")
  List<HourClickRow> findHourlyClicks(@Param("linkId") Long linkId, @Param("tz") String timezone);

  @Query(
      "SELECT FUNCTION('DAYOFWEEK', FUNCTION('CONVERT_TZ', c.clickedAt, '+00:00', :tz)) AS dow, "
          + "COUNT(c) AS count "
          + "FROM ClickEventEntity c WHERE c.linkId = :linkId AND c.bot = false "
          + "GROUP BY FUNCTION('DAYOFWEEK', FUNCTION('CONVERT_TZ', c.clickedAt, '+00:00', :tz)) "
          + "ORDER BY dow")
  List<DayOfWeekClickRow> findDayOfWeekClicks(
      @Param("linkId") Long linkId, @Param("tz") String timezone);

  @Query(
      "SELECT FUNCTION('DAYOFWEEK', FUNCTION('CONVERT_TZ', c.clickedAt, '+00:00', :tz)) AS dow, "
          + "FUNCTION('HOUR', FUNCTION('CONVERT_TZ', c.clickedAt, '+00:00', :tz)) AS hour, "
          + "COUNT(c) AS count "
          + "FROM ClickEventEntity c WHERE c.linkId = :linkId AND c.bot = false "
          + "GROUP BY dow, hour ORDER BY dow, hour")
  List<HeatmapRow> findHeatmap(@Param("linkId") Long linkId, @Param("tz") String timezone);

  @Query(
      "SELECT FUNCTION('HOUR', FUNCTION('CONVERT_TZ', c.clickedAt, '+00:00', :tz)) AS hour, "
          + "COUNT(c) AS count "
          + "FROM ClickEventEntity c WHERE c.linkId IN :linkIds AND c.bot = false "
          + "AND c.clickedAt >= :since "
          + "GROUP BY FUNCTION('HOUR', FUNCTION('CONVERT_TZ', c.clickedAt, '+00:00', :tz)) "
          + "ORDER BY hour")
  List<HourClickRow> findHourlyClicksByLinkIdsSince(
      @Param("linkIds") List<Long> linkIds,
      @Param("since") Instant since,
      @Param("tz") String timezone);

  @Query(
      "SELECT FUNCTION('DATE', FUNCTION('CONVERT_TZ', c.clickedAt, '+00:00', :tz)) AS day, "
          + "COUNT(c) AS count "
          + "FROM ClickEventEntity c WHERE c.linkId IN :linkIds AND c.bot = false "
          + "AND c.clickedAt >= :since "
          + "GROUP BY FUNCTION('DATE', FUNCTION('CONVERT_TZ', c.clickedAt, '+00:00', :tz)) "
          + "ORDER BY day")
  List<DailyClickRow> findDailyClicksByLinkIdsSince(
      @Param("linkIds") List<Long> linkIds,
      @Param("since") Instant since,
      @Param("tz") String timezone);

  @Query(
      "SELECT FUNCTION('DAYOFWEEK', FUNCTION('CONVERT_TZ', c.clickedAt, '+00:00', :tz)) AS dow, "
          + "FUNCTION('HOUR', FUNCTION('CONVERT_TZ', c.clickedAt, '+00:00', :tz)) AS hour, "
          + "COUNT(c) AS count "
          + "FROM ClickEventEntity c WHERE c.linkId IN :linkIds AND c.bot = false "
          + "AND c.clickedAt >= :since "
          + "GROUP BY dow, hour ORDER BY dow, hour")
  List<HeatmapRow> findHeatmapByLinkIdsSince(
      @Param("linkIds") List<Long> linkIds,
      @Param("since") Instant since,
      @Param("tz") String timezone);

  /** UTC epoch 일자로 묶고, 하한도 epoch에서 변환해 세션 시간대가 날짜와 기간을 바꾸지 않게 한다. */
  @Query(
      value =
          """
      SELECT c.link_id AS linkId,
             FLOOR(UNIX_TIMESTAMP(c.clicked_at) / 86400) AS epochDay,
             COUNT(*) AS count
      FROM click_event c
      WHERE c.link_id IN (:ids) AND c.is_bot = false
        AND c.clicked_at >= FROM_UNIXTIME(:fromEpoch)
      GROUP BY c.link_id, FLOOR(UNIX_TIMESTAMP(c.clicked_at) / 86400)
      """,
      nativeQuery = true)
  List<UtcDailyClicksByLinkRow> findUtcDailyClicksByLinkIdsSince(
      @Param("ids") List<Long> ids, @Param("fromEpoch") BigDecimal fromEpoch);

  interface UtcDailyClicksByLinkRow {
    Long getLinkId();

    Long getEpochDay();

    Long getCount();
  }
}
