package com.example.short_link.link.stats.domain.repository;

import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.stats.domain.ClickEventEntity;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.DailyClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.DailyClicksByLinkRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.DayOfWeekClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.HeatmapRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.HourClickRow;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Time-bucketed aggregations — daily / hourly / day-of-week / heatmap. Single-link variants drive
 * the owner stats page; the {@code ByLinkIds} variants aggregate across a campaign's batch links so
 * the campaign view can show one timeline for the whole campaign.
 */
public interface ClickTimeReadRepository extends Repository<ClickEventEntity, Long> {

  @Query(
      "SELECT FUNCTION('DATE', FUNCTION('CONVERT_TZ', c.clickedAt, '+00:00', :tz)) AS day, "
          + "COUNT(c) AS count "
          + "FROM ClickEventEntity c WHERE c.linkId = :linkId AND c.bot = false "
          + "AND c.clickedAt >= :from "
          + "GROUP BY FUNCTION('DATE', FUNCTION('CONVERT_TZ', c.clickedAt, '+00:00', :tz)) "
          + "ORDER BY day")
  List<DailyClickRow> findDailyClicks(
      @Param("linkId") LinkId linkId, @Param("from") Instant from, @Param("tz") String timezone);

  @Query(
      "SELECT FUNCTION('HOUR', FUNCTION('CONVERT_TZ', c.clickedAt, '+00:00', :tz)) AS hour, "
          + "COUNT(c) AS count "
          + "FROM ClickEventEntity c WHERE c.linkId = :linkId AND c.bot = false "
          + "GROUP BY FUNCTION('HOUR', FUNCTION('CONVERT_TZ', c.clickedAt, '+00:00', :tz)) "
          + "ORDER BY hour")
  List<HourClickRow> findHourlyClicks(@Param("linkId") LinkId linkId, @Param("tz") String timezone);

  @Query(
      "SELECT FUNCTION('DAYOFWEEK', FUNCTION('CONVERT_TZ', c.clickedAt, '+00:00', :tz)) AS dow, "
          + "COUNT(c) AS count "
          + "FROM ClickEventEntity c WHERE c.linkId = :linkId AND c.bot = false "
          + "GROUP BY FUNCTION('DAYOFWEEK', FUNCTION('CONVERT_TZ', c.clickedAt, '+00:00', :tz)) "
          + "ORDER BY dow")
  List<DayOfWeekClickRow> findDayOfWeekClicks(
      @Param("linkId") LinkId linkId, @Param("tz") String timezone);

  @Query(
      "SELECT FUNCTION('DAYOFWEEK', FUNCTION('CONVERT_TZ', c.clickedAt, '+00:00', :tz)) AS dow, "
          + "FUNCTION('HOUR', FUNCTION('CONVERT_TZ', c.clickedAt, '+00:00', :tz)) AS hour, "
          + "COUNT(c) AS count "
          + "FROM ClickEventEntity c WHERE c.linkId = :linkId AND c.bot = false "
          + "GROUP BY dow, hour ORDER BY dow, hour")
  List<HeatmapRow> findHeatmap(@Param("linkId") LinkId linkId, @Param("tz") String timezone);

  /**
   * Campaign-aggregate variant — sums across a campaign's batch links since {@code
   * campaign.startsAt} (test scans excluded).
   */
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

  /** my-links cards — sparkline data per link, grouped without timezone conversion. */
  @Query(
      "SELECT c.linkId AS linkId, FUNCTION('DATE', c.clickedAt) AS day, COUNT(c) AS count "
          + "FROM ClickEventEntity c "
          + "WHERE c.linkId IN :ids AND c.bot = false AND c.clickedAt >= :from "
          + "GROUP BY c.linkId, FUNCTION('DATE', c.clickedAt)")
  List<DailyClicksByLinkRow> findDailyClicksByLinkIdsSince(
      @Param("ids") List<Long> ids, @Param("from") Instant from);
}
