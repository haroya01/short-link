package com.example.short_link.link.stats.infrastructure.persistence;

import com.example.short_link.link.stats.domain.ClickEventEntity;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.DailyClickBucketRow;
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

  /** Boundaries come from the owner's ZoneId, including historical daylight-saving offsets. */
  @Query(
      value =
          """
      SELECT c.link_id AS linkId,
        CASE
          WHEN c.clicked_at < FROM_UNIXTIME(:b1) THEN 0
          WHEN c.clicked_at < FROM_UNIXTIME(:b2) THEN 1
          WHEN c.clicked_at < FROM_UNIXTIME(:b3) THEN 2
          WHEN c.clicked_at < FROM_UNIXTIME(:b4) THEN 3
          WHEN c.clicked_at < FROM_UNIXTIME(:b5) THEN 4
          WHEN c.clicked_at < FROM_UNIXTIME(:b6) THEN 5
          ELSE 6
        END AS bucket,
        COUNT(*) AS count
      FROM click_event c
      WHERE c.link_id IN (:ids) AND c.is_bot = false
        AND c.clicked_at >= FROM_UNIXTIME(:b0) AND c.clicked_at <= FROM_UNIXTIME(:until)
      GROUP BY c.link_id, bucket
      """,
      nativeQuery = true)
  List<DailyClickBucketRow> findDailyClickBucketsByLinkIds(
      @Param("ids") List<Long> ids,
      @Param("b0") BigDecimal b0,
      @Param("b1") BigDecimal b1,
      @Param("b2") BigDecimal b2,
      @Param("b3") BigDecimal b3,
      @Param("b4") BigDecimal b4,
      @Param("b5") BigDecimal b5,
      @Param("b6") BigDecimal b6,
      @Param("until") BigDecimal until);

  @Query(
      "SELECT FUNCTION('DATE', FUNCTION('CONVERT_TZ', timestampadd(second, floor(FUNCTION('UNIX_TIMESTAMP', c.clickedAt)), datetime 1970-01-01 00:00:00), '+00:00', :tz)) AS day, "
          + "COUNT(c) AS count "
          + "FROM ClickEventEntity c WHERE c.linkId = :linkId AND c.bot = false "
          + "AND c.clickedAt >= :from "
          + "GROUP BY FUNCTION('DATE', FUNCTION('CONVERT_TZ', timestampadd(second, floor(FUNCTION('UNIX_TIMESTAMP', c.clickedAt)), datetime 1970-01-01 00:00:00), '+00:00', :tz)) "
          + "ORDER BY day")
  List<DailyClickRow> findDailyClicks(
      @Param("linkId") Long linkId, @Param("from") Instant from, @Param("tz") String timezone);

  @Query(
      "SELECT FUNCTION('HOUR', FUNCTION('CONVERT_TZ', timestampadd(second, floor(FUNCTION('UNIX_TIMESTAMP', c.clickedAt)), datetime 1970-01-01 00:00:00), '+00:00', :tz)) AS hour, "
          + "COUNT(c) AS count "
          + "FROM ClickEventEntity c WHERE c.linkId = :linkId AND c.bot = false "
          + "GROUP BY FUNCTION('HOUR', FUNCTION('CONVERT_TZ', timestampadd(second, floor(FUNCTION('UNIX_TIMESTAMP', c.clickedAt)), datetime 1970-01-01 00:00:00), '+00:00', :tz)) "
          + "ORDER BY hour")
  List<HourClickRow> findHourlyClicks(@Param("linkId") Long linkId, @Param("tz") String timezone);

  @Query(
      "SELECT FUNCTION('DAYOFWEEK', FUNCTION('CONVERT_TZ', timestampadd(second, floor(FUNCTION('UNIX_TIMESTAMP', c.clickedAt)), datetime 1970-01-01 00:00:00), '+00:00', :tz)) AS dow, "
          + "COUNT(c) AS count "
          + "FROM ClickEventEntity c WHERE c.linkId = :linkId AND c.bot = false "
          + "GROUP BY FUNCTION('DAYOFWEEK', FUNCTION('CONVERT_TZ', timestampadd(second, floor(FUNCTION('UNIX_TIMESTAMP', c.clickedAt)), datetime 1970-01-01 00:00:00), '+00:00', :tz)) "
          + "ORDER BY dow")
  List<DayOfWeekClickRow> findDayOfWeekClicks(
      @Param("linkId") Long linkId, @Param("tz") String timezone);

  @Query(
      "SELECT FUNCTION('DAYOFWEEK', FUNCTION('CONVERT_TZ', timestampadd(second, floor(FUNCTION('UNIX_TIMESTAMP', c.clickedAt)), datetime 1970-01-01 00:00:00), '+00:00', :tz)) AS dow, "
          + "FUNCTION('HOUR', FUNCTION('CONVERT_TZ', timestampadd(second, floor(FUNCTION('UNIX_TIMESTAMP', c.clickedAt)), datetime 1970-01-01 00:00:00), '+00:00', :tz)) AS hour, "
          + "COUNT(c) AS count "
          + "FROM ClickEventEntity c WHERE c.linkId = :linkId AND c.bot = false "
          + "GROUP BY dow, hour ORDER BY dow, hour")
  List<HeatmapRow> findHeatmap(@Param("linkId") Long linkId, @Param("tz") String timezone);

  @Query(
      "SELECT FUNCTION('HOUR', FUNCTION('CONVERT_TZ', timestampadd(second, floor(FUNCTION('UNIX_TIMESTAMP', c.clickedAt)), datetime 1970-01-01 00:00:00), '+00:00', :tz)) AS hour, "
          + "COUNT(c) AS count "
          + "FROM ClickEventEntity c WHERE c.linkId IN :linkIds AND c.bot = false "
          + "AND c.clickedAt >= :since "
          + "GROUP BY FUNCTION('HOUR', FUNCTION('CONVERT_TZ', timestampadd(second, floor(FUNCTION('UNIX_TIMESTAMP', c.clickedAt)), datetime 1970-01-01 00:00:00), '+00:00', :tz)) "
          + "ORDER BY hour")
  List<HourClickRow> findHourlyClicksByLinkIdsSince(
      @Param("linkIds") List<Long> linkIds,
      @Param("since") Instant since,
      @Param("tz") String timezone);

  @Query(
      "SELECT FUNCTION('DATE', FUNCTION('CONVERT_TZ', timestampadd(second, floor(FUNCTION('UNIX_TIMESTAMP', c.clickedAt)), datetime 1970-01-01 00:00:00), '+00:00', :tz)) AS day, "
          + "COUNT(c) AS count "
          + "FROM ClickEventEntity c WHERE c.linkId IN :linkIds AND c.bot = false "
          + "AND c.clickedAt >= :since "
          + "GROUP BY FUNCTION('DATE', FUNCTION('CONVERT_TZ', timestampadd(second, floor(FUNCTION('UNIX_TIMESTAMP', c.clickedAt)), datetime 1970-01-01 00:00:00), '+00:00', :tz)) "
          + "ORDER BY day")
  List<DailyClickRow> findDailyClicksByLinkIdsSince(
      @Param("linkIds") List<Long> linkIds,
      @Param("since") Instant since,
      @Param("tz") String timezone);

  @Query(
      "SELECT FUNCTION('DAYOFWEEK', FUNCTION('CONVERT_TZ', timestampadd(second, floor(FUNCTION('UNIX_TIMESTAMP', c.clickedAt)), datetime 1970-01-01 00:00:00), '+00:00', :tz)) AS dow, "
          + "FUNCTION('HOUR', FUNCTION('CONVERT_TZ', timestampadd(second, floor(FUNCTION('UNIX_TIMESTAMP', c.clickedAt)), datetime 1970-01-01 00:00:00), '+00:00', :tz)) AS hour, "
          + "COUNT(c) AS count "
          + "FROM ClickEventEntity c WHERE c.linkId IN :linkIds AND c.bot = false "
          + "AND c.clickedAt >= :since "
          + "GROUP BY dow, hour ORDER BY dow, hour")
  List<HeatmapRow> findHeatmapByLinkIdsSince(
      @Param("linkIds") List<Long> linkIds,
      @Param("since") Instant since,
      @Param("tz") String timezone);
}
