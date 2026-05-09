package com.example.short_link.admin.application;

import com.example.short_link.link.domain.ClickEventEntity;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdminAnalyticsRepository extends JpaRepository<ClickEventEntity, Long> {

  @Query(
      value =
          "SELECT YEARWEEK(created_at, 3) AS cohortKey, COUNT(*) AS count "
              + "FROM users WHERE created_at >= :since "
              + "GROUP BY cohortKey ORDER BY cohortKey",
      nativeQuery = true)
  List<CohortSizeRow> cohortSizes(@Param("since") Instant since);

  @Query(
      value =
          "SELECT YEARWEEK(u.created_at, 3) AS cohortKey, "
              + "YEARWEEK(c.clicked_at, 3) AS activityKey, "
              + "COUNT(DISTINCT u.id) AS activeUsers "
              + "FROM users u JOIN link l ON l.user_id = u.id "
              + "JOIN click_event c ON c.link_id = l.id "
              + "WHERE u.created_at >= :since AND c.is_bot = 0 "
              + "AND c.clicked_at >= u.created_at "
              + "GROUP BY cohortKey, activityKey "
              + "ORDER BY cohortKey, activityKey",
      nativeQuery = true)
  List<CohortActivityRow> cohortActivity(@Param("since") Instant since);

  @Query(
      value =
          "SELECT TIMESTAMPDIFF(DAY, l.created_at, c.clicked_at) AS day, "
              + "COUNT(*) AS clicks, COUNT(DISTINCT l.id) AS links "
              + "FROM click_event c JOIN link l ON l.id = c.link_id "
              + "WHERE c.is_bot = 0 "
              + "AND TIMESTAMPDIFF(DAY, l.created_at, c.clicked_at) BETWEEN 0 AND :maxDay "
              + "GROUP BY day ORDER BY day",
      nativeQuery = true)
  List<LifecycleDayRow> lifecycleDays(@Param("maxDay") int maxDay);

  @Query(
      value =
          "SELECT FUNCTION('DATE', FUNCTION('CONVERT_TZ', c.clickedAt, '+00:00', :tz)) AS bucket, "
              + "COUNT(DISTINCT l.userId) AS active "
              + "FROM ClickEventEntity c JOIN LinkEntity l ON l.id = c.linkId "
              + "WHERE c.bot = false AND l.userId IS NOT NULL AND c.clickedAt >= :since "
              + "GROUP BY FUNCTION('DATE', FUNCTION('CONVERT_TZ', c.clickedAt, '+00:00', :tz)) "
              + "ORDER BY bucket")
  List<ActivePerDayRow> dailyActiveUsers(
      @Param("since") Instant since, @Param("tz") String timezone);

  @Query(
      value =
          "SELECT YEARWEEK(c.clicked_at, 3) AS bucket, "
              + "COUNT(DISTINCT l.user_id) AS active "
              + "FROM click_event c JOIN link l ON l.id = c.link_id "
              + "WHERE c.is_bot = 0 AND l.user_id IS NOT NULL AND c.clicked_at >= :since "
              + "GROUP BY bucket ORDER BY bucket",
      nativeQuery = true)
  List<ActivePerBucketRow> weeklyActiveUsers(@Param("since") Instant since);

  @Query(
      value =
          "SELECT DATE_FORMAT(c.clicked_at, '%Y-%m') AS bucket, "
              + "COUNT(DISTINCT l.user_id) AS active "
              + "FROM click_event c JOIN link l ON l.id = c.link_id "
              + "WHERE c.is_bot = 0 AND l.user_id IS NOT NULL AND c.clicked_at >= :since "
              + "GROUP BY bucket ORDER BY bucket",
      nativeQuery = true)
  List<ActivePerBucketStringRow> monthlyActiveUsers(@Param("since") Instant since);

  interface CohortSizeRow {
    Integer getCohortKey();

    Long getCount();
  }

  interface CohortActivityRow {
    Integer getCohortKey();

    Integer getActivityKey();

    Long getActiveUsers();
  }

  interface LifecycleDayRow {
    Integer getDay();

    Long getClicks();

    Long getLinks();
  }

  interface ActivePerDayRow {
    LocalDate getBucket();

    Long getActive();
  }

  interface ActivePerBucketRow {
    Integer getBucket();

    Long getActive();
  }

  interface ActivePerBucketStringRow {
    String getBucket();

    Long getActive();
  }
}
