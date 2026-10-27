package com.example.short_link.admin.infrastructure.persistence;

import com.example.short_link.admin.domain.repository.AdminAnalyticsRepository;
import com.example.short_link.admin.domain.repository.AdminAnalyticsRepository.ActivePerBucketRow;
import com.example.short_link.admin.domain.repository.AdminAnalyticsRepository.ActivePerBucketStringRow;
import com.example.short_link.admin.domain.repository.AdminAnalyticsRepository.ActivePerDayRow;
import com.example.short_link.admin.domain.repository.AdminAnalyticsRepository.CohortActivityRow;
import com.example.short_link.admin.domain.repository.AdminAnalyticsRepository.CohortSizeRow;
import com.example.short_link.admin.domain.repository.AdminAnalyticsRepository.LifecycleDayRow;
import com.example.short_link.link.stats.domain.ClickEventEntity;
import com.example.short_link.link.stats.domain.ClickRollup;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaAdminAnalyticsRepository
    extends JpaRepository<ClickEventEntity, Long>, AdminAnalyticsRepository {

  @Override
  @Query(
      value =
          "SELECT YEARWEEK(created_at, 3) AS cohortKey, COUNT(*) AS count "
              + "FROM users WHERE created_at >= :since "
              + "GROUP BY cohortKey ORDER BY cohortKey",
      nativeQuery = true)
  List<CohortSizeRow> cohortSizes(@Param("since") Instant since);

  @Override
  @Query(
      value =
          "SELECT YEARWEEK(u.created_at, 3) AS cohortKey, "
              + "YEARWEEK(c.at, 3) AS activityKey, "
              + "COUNT(DISTINCT u.id) AS activeUsers "
              + "FROM users u JOIN link l ON l.user_id = u.id "
              + "JOIN (SELECT link_id, clicked_at AS at, clicked_at AS last_at FROM click_event "
              + "WHERE is_bot = 0 AND clicked_at >= "
              + ClickRollup.ROLLED_UNTIL
              + " UNION ALL SELECT link_id, hour_start, last_human_at FROM click_hourly "
              + "WHERE humans > 0) c ON c.link_id = l.id "
              + "WHERE u.created_at >= :since "
              + "AND c.last_at >= u.created_at "
              + "GROUP BY cohortKey, activityKey "
              + "ORDER BY cohortKey, activityKey",
      nativeQuery = true)
  List<CohortActivityRow> cohortActivity(@Param("since") Instant since);

  @Override
  @Query(
      value =
          "SELECT day, CAST(SUM(n) AS SIGNED) AS clicks, COUNT(DISTINCT link_id) AS links "
              + "FROM (SELECT TIMESTAMPDIFF(DAY, l.created_at, c.clicked_at) AS day, "
              + "l.id AS link_id, COUNT(*) AS n FROM click_event c JOIN link l ON l.id = c.link_id "
              + "WHERE c.is_bot = 0 AND c.clicked_at >= "
              + ClickRollup.ROLLED_UNTIL
              + " AND TIMESTAMPDIFF(DAY, l.created_at, c.clicked_at) BETWEEN 0 AND :maxDay "
              + "GROUP BY day, l.id UNION ALL "
              + "SELECT h.age_day, h.link_id, SUM(h.humans) FROM click_hourly h "
              + "JOIN link l ON l.id = h.link_id WHERE h.humans > 0 "
              + "AND h.age_day BETWEEN 0 AND :maxDay GROUP BY h.age_day, h.link_id) u "
              + "GROUP BY day ORDER BY day",
      nativeQuery = true)
  List<LifecycleDayRow> lifecycleDays(@Param("maxDay") int maxDay);

  @Override
  @Query(
      value =
          "SELECT FUNCTION('DATE', FUNCTION('CONVERT_TZ', timestampadd(second, floor(FUNCTION('UNIX_TIMESTAMP', c.clickedAt)), datetime 1970-01-01 00:00:00), '+00:00', :tz)) AS bucket, "
              + "COUNT(DISTINCT l.userId) AS active "
              + "FROM ClickEventEntity c JOIN LinkEntity l ON l.id = c.linkId "
              + "WHERE c.bot = false AND l.userId IS NOT NULL AND c.clickedAt >= :since "
              + "GROUP BY FUNCTION('DATE', FUNCTION('CONVERT_TZ', timestampadd(second, floor(FUNCTION('UNIX_TIMESTAMP', c.clickedAt)), datetime 1970-01-01 00:00:00), '+00:00', :tz)) "
              + "ORDER BY bucket")
  List<ActivePerDayRow> dailyActiveUsers(
      @Param("since") Instant since, @Param("tz") String timezone);

  @Override
  @Query(
      value =
          "SELECT YEARWEEK(c.clicked_at, 3) AS bucket, "
              + "COUNT(DISTINCT l.user_id) AS active "
              + "FROM click_event c JOIN link l ON l.id = c.link_id "
              + "WHERE c.is_bot = 0 AND l.user_id IS NOT NULL AND c.clicked_at >= :since "
              + "GROUP BY bucket ORDER BY bucket",
      nativeQuery = true)
  List<ActivePerBucketRow> weeklyActiveUsers(@Param("since") Instant since);

  @Override
  @Query(
      value =
          "SELECT DATE_FORMAT(c.at, '%Y-%m') AS bucket, "
              + "COUNT(DISTINCT l.user_id) AS active "
              + "FROM (SELECT link_id, clicked_at AS at FROM click_event WHERE is_bot = 0 "
              + "AND clicked_at >= :since AND clicked_at >= "
              + ClickRollup.ROLLED_UNTIL
              + " UNION ALL SELECT link_id, hour_start FROM click_hourly WHERE humans > 0 "
              + "AND last_human_at >= :since) c JOIN link l ON l.id = c.link_id "
              + "WHERE l.user_id IS NOT NULL "
              + "GROUP BY bucket ORDER BY bucket",
      nativeQuery = true)
  List<ActivePerBucketStringRow> monthlyActiveUsers(@Param("since") Instant since);
}
