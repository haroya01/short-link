package com.example.short_link.link.stats.infrastructure.persistence;

import com.example.short_link.link.stats.domain.ClickEventEntity;
import com.example.short_link.link.stats.domain.ClickRollup;
import com.example.short_link.link.stats.domain.projection.ClickProjections.DailyClickBucketRow;
import com.example.short_link.link.stats.domain.projection.ClickProjections.DailyClickRow;
import com.example.short_link.link.stats.domain.projection.ClickProjections.HeatmapRow;
import com.example.short_link.link.stats.domain.projection.ClickProjections.HourClickRow;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface JpaClickTimeReadRepository extends Repository<ClickEventEntity, Long> {

  String RAW = " AND clicked_at >= " + ClickRollup.ROLLED_UNTIL;

  String FIRST_QUARTER_END = "FROM_UNIXTIME(CEIL(:from / 900) * 900)";

  String RECENT_OR_FIRST_QUARTER =
      " AND (clicked_at >= "
          + ClickRollup.AGGREGATED_UNTIL
          + " OR clicked_at < "
          + FIRST_QUARTER_END
          + ")";

  // Boundaries come from the owner's ZoneId, including historical daylight-saving offsets.
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
      value =
          "SELECT d AS day, CAST(SUM(n) AS SIGNED) AS count FROM ("
              + "SELECT DATE(CONVERT_TZ(TIMESTAMPADD(SECOND, FLOOR(UNIX_TIMESTAMP(clicked_at)), '1970-01-01'), '+00:00', :tz)) AS d, COUNT(*) AS n FROM click_event "
              + "WHERE link_id = :linkId AND is_bot = 0 AND clicked_at >= FROM_UNIXTIME(:from) AND clicked_at < FROM_UNIXTIME(:until)"
              + RECENT_OR_FIRST_QUARTER
              + " GROUP BY d UNION ALL "
              + "SELECT DATE(CONVERT_TZ(TIMESTAMPADD(SECOND, FLOOR(UNIX_TIMESTAMP(quarter_start)), '1970-01-01'), '+00:00', :tz)) AS d, SUM(humans) FROM click_recent_quarter "
              + "WHERE link_id = :linkId AND humans > 0 AND quarter_start >= "
              + FIRST_QUARTER_END
              + " AND quarter_start < FROM_UNIXTIME(:until) GROUP BY d"
              + ") u GROUP BY d ORDER BY d",
      nativeQuery = true)
  List<DailyClickRow> findDailyClicks(
      @Param("linkId") Long linkId,
      @Param("from") BigDecimal from,
      @Param("until") BigDecimal until,
      @Param("tz") String timezone);

  @Query(
      value =
          "SELECT d AS dow, h AS hour, CAST(SUM(n) AS SIGNED) AS count FROM ("
              + "SELECT DAYOFWEEK(CONVERT_TZ(TIMESTAMPADD(SECOND, FLOOR(UNIX_TIMESTAMP(clicked_at)), '1970-01-01'), '+00:00', :tz)) AS d, HOUR(CONVERT_TZ(TIMESTAMPADD(SECOND, FLOOR(UNIX_TIMESTAMP(clicked_at)), '1970-01-01'), '+00:00', :tz)) AS h, COUNT(*) AS n FROM click_event "
              + "WHERE link_id = :linkId AND is_bot = 0 AND clicked_at >= FROM_UNIXTIME(:from) AND clicked_at < FROM_UNIXTIME(:until)"
              + RAW
              + RECENT_OR_FIRST_QUARTER
              + " GROUP BY d, h UNION ALL "
              + "SELECT DAYOFWEEK(CONVERT_TZ(TIMESTAMPADD(SECOND, FLOOR(UNIX_TIMESTAMP(hour_start)), '1970-01-01'), '+00:00', :tz)) AS d, HOUR(CONVERT_TZ(TIMESTAMPADD(SECOND, FLOOR(UNIX_TIMESTAMP(hour_start)), '1970-01-01'), '+00:00', :tz)) AS h, SUM(humans) FROM click_hourly "
              + "WHERE link_id = :linkId AND humans > 0 AND hour_start > TIMESTAMPADD(HOUR, -1, FROM_UNIXTIME(:from)) AND hour_start < FROM_UNIXTIME(:until) GROUP BY d, h UNION ALL "
              + "SELECT DAYOFWEEK(CONVERT_TZ(TIMESTAMPADD(SECOND, FLOOR(UNIX_TIMESTAMP(quarter_start)), '1970-01-01'), '+00:00', :tz)) AS d, HOUR(CONVERT_TZ(TIMESTAMPADD(SECOND, FLOOR(UNIX_TIMESTAMP(quarter_start)), '1970-01-01'), '+00:00', :tz)) AS h, SUM(humans) FROM click_recent_quarter "
              + "WHERE link_id = :linkId AND humans > 0 AND quarter_start >= "
              + FIRST_QUARTER_END
              + " AND quarter_start < FROM_UNIXTIME(:until) GROUP BY d, h"
              + ") u GROUP BY d, h ORDER BY d, h",
      nativeQuery = true)
  List<HeatmapRow> findHeatmap(
      @Param("linkId") Long linkId,
      @Param("from") BigDecimal from,
      @Param("until") BigDecimal until,
      @Param("tz") String timezone);

  @Query(
      value =
          "SELECT h AS hour, CAST(SUM(n) AS SIGNED) AS count FROM ("
              + "SELECT HOUR(CONVERT_TZ(TIMESTAMPADD(SECOND, FLOOR(UNIX_TIMESTAMP(clicked_at)), '1970-01-01'), '+00:00', :tz)) AS h, COUNT(*) AS n FROM click_event "
              + "WHERE link_id IN (:linkIds) AND is_bot = 0 AND clicked_at >= FROM_UNIXTIME(:since)"
              + RAW
              + " GROUP BY h UNION ALL "
              + "SELECT HOUR(CONVERT_TZ(TIMESTAMPADD(SECOND, FLOOR(UNIX_TIMESTAMP(hour_start)), '1970-01-01'), '+00:00', :tz)) AS h, SUM(humans) FROM click_hourly "
              + "WHERE link_id IN (:linkIds) AND humans > 0 AND last_human_at >= FROM_UNIXTIME(:since) GROUP BY h"
              + ") u GROUP BY h ORDER BY h",
      nativeQuery = true)
  List<HourClickRow> findHourlyClicksByLinkIdsSince(
      @Param("linkIds") List<Long> linkIds,
      @Param("since") BigDecimal since,
      @Param("tz") String timezone);

  @Query(
      value =
          "SELECT d AS day, CAST(SUM(n) AS SIGNED) AS count FROM ("
              + "SELECT DATE(CONVERT_TZ(TIMESTAMPADD(SECOND, FLOOR(UNIX_TIMESTAMP(clicked_at)), '1970-01-01'), '+00:00', :tz)) AS d, COUNT(*) AS n FROM click_event "
              + "WHERE link_id IN (:linkIds) AND is_bot = 0 AND clicked_at >= FROM_UNIXTIME(:since)"
              + RAW
              + " GROUP BY d UNION ALL "
              + "SELECT DATE(CONVERT_TZ(TIMESTAMPADD(SECOND, FLOOR(UNIX_TIMESTAMP(hour_start)), '1970-01-01'), '+00:00', :tz)) AS d, SUM(humans) FROM click_hourly "
              + "WHERE link_id IN (:linkIds) AND humans > 0 AND last_human_at >= FROM_UNIXTIME(:since) GROUP BY d"
              + ") u GROUP BY d ORDER BY d",
      nativeQuery = true)
  List<DailyClickRow> findDailyClicksByLinkIdsSince(
      @Param("linkIds") List<Long> linkIds,
      @Param("since") BigDecimal since,
      @Param("tz") String timezone);

  @Query(
      value =
          "SELECT d AS dow, h AS hour, CAST(SUM(n) AS SIGNED) AS count FROM ("
              + "SELECT DAYOFWEEK(CONVERT_TZ(TIMESTAMPADD(SECOND, FLOOR(UNIX_TIMESTAMP(clicked_at)), '1970-01-01'), '+00:00', :tz)) AS d, HOUR(CONVERT_TZ(TIMESTAMPADD(SECOND, FLOOR(UNIX_TIMESTAMP(clicked_at)), '1970-01-01'), '+00:00', :tz)) AS h, COUNT(*) AS n FROM click_event "
              + "WHERE link_id IN (:linkIds) AND is_bot = 0 AND clicked_at >= FROM_UNIXTIME(:since)"
              + RAW
              + " GROUP BY d, h UNION ALL "
              + "SELECT DAYOFWEEK(CONVERT_TZ(TIMESTAMPADD(SECOND, FLOOR(UNIX_TIMESTAMP(hour_start)), '1970-01-01'), '+00:00', :tz)) AS d, HOUR(CONVERT_TZ(TIMESTAMPADD(SECOND, FLOOR(UNIX_TIMESTAMP(hour_start)), '1970-01-01'), '+00:00', :tz)) AS h, SUM(humans) FROM click_hourly "
              + "WHERE link_id IN (:linkIds) AND humans > 0 AND last_human_at >= FROM_UNIXTIME(:since) GROUP BY d, h"
              + ") u GROUP BY d, h ORDER BY d, h",
      nativeQuery = true)
  List<HeatmapRow> findHeatmapByLinkIdsSince(
      @Param("linkIds") List<Long> linkIds,
      @Param("since") BigDecimal since,
      @Param("tz") String timezone);
}
