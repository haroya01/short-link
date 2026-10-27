package com.example.short_link.admin.infrastructure.persistence;

import com.example.short_link.admin.domain.repository.AdminMetricsRepository.DailyRow;
import com.example.short_link.admin.domain.repository.AdminMetricsRepository.LinkMetricRow;
import com.example.short_link.admin.domain.repository.AdminMetricsRepository.LinkStatRow;
import com.example.short_link.admin.domain.repository.AdminMetricsRepository.RecentClickRow;
import com.example.short_link.admin.domain.repository.AdminMetricsRepository.RecentLinkRow;
import com.example.short_link.admin.domain.repository.AdminMetricsRepository.UserStatRow;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.stats.domain.ClickEventEntity;
import com.example.short_link.link.stats.domain.ClickRollup;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaAdminMetricsRepository extends JpaRepository<ClickEventEntity, Long> {

  String HUMANS_PER_LINK =
      "(SELECT link_id, COUNT(*) AS n FROM click_event WHERE is_bot = 0 AND clicked_at >= "
          + ClickRollup.ROLLED_UNTIL
          + " GROUP BY link_id UNION ALL SELECT link_id, SUM(humans) FROM click_hourly "
          + "GROUP BY link_id) x ";

  String ROLLED_UNTIL_JPQL = "(SELECT s.rolledUntil FROM ClickRollupStateEntity s)";

  @Query("SELECT COUNT(u) FROM UserEntity u")
  long totalUsers();

  @Query("SELECT COUNT(l) FROM LinkEntity l")
  long totalLinks();

  @Query("SELECT COUNT(u) FROM UserEntity u WHERE u.createdAt > :since")
  long usersCreatedAfter(@Param("since") Instant since);

  @Query("SELECT COUNT(l) FROM LinkEntity l WHERE l.createdAt > :since")
  long linksCreatedAfter(@Param("since") Instant since);

  @Query("SELECT COUNT(l) FROM LinkEntity l WHERE l.userId IS NULL")
  long anonymousLinks();

  @Query("SELECT COUNT(l) FROM LinkEntity l WHERE l.expiresAt < :when")
  long linksExpiredBefore(@Param("when") Instant when);

  @Query(
      value =
          "SELECT (SELECT COUNT(*) FROM click_event WHERE clicked_at >= "
              + ClickRollup.ROLLED_UNTIL
              + " AND link_id NOT IN (SELECT s.id FROM link s WHERE s.user_id = :smokeUserId)) "
              + "+ (SELECT COALESCE(SUM(humans + bots), 0) FROM click_hourly "
              + "WHERE link_id NOT IN (SELECT s.id FROM link s WHERE s.user_id = :smokeUserId))",
      nativeQuery = true)
  long totalClicks(@Param("smokeUserId") long smokeUserId);

  @Query(
      "SELECT COUNT(c) FROM ClickEventEntity c WHERE c.clickedAt >= :since AND c.linkId NOT IN (SELECT s.id FROM LinkEntity s WHERE s.userId = :smokeUserId)")
  long clicksSince(@Param("since") Instant since, @Param("smokeUserId") long smokeUserId);

  @Query(
      "SELECT COUNT(l) FROM LinkEntity l WHERE NOT EXISTS "
          + "(SELECT 1 FROM ClickEventEntity c WHERE c.linkId = l.id) AND NOT EXISTS "
          + "(SELECT 1 FROM ClickHourlyEntity h WHERE h.linkId = l.id)")
  long linksWithoutClicks();

  @Query(
      "SELECT FUNCTION('DATE', FUNCTION('CONVERT_TZ', timestampadd(second, floor(FUNCTION('UNIX_TIMESTAMP', u.createdAt)), datetime 1970-01-01 00:00:00), '+00:00', :tz)) AS day, "
          + "COUNT(u) AS count "
          + "FROM UserEntity u WHERE u.createdAt >= :since "
          + "GROUP BY FUNCTION('DATE', FUNCTION('CONVERT_TZ', timestampadd(second, floor(FUNCTION('UNIX_TIMESTAMP', u.createdAt)), datetime 1970-01-01 00:00:00), '+00:00', :tz)) "
          + "ORDER BY day")
  List<DailyRow> dailySignupsSince(@Param("since") Instant since, @Param("tz") String tz);

  @Query(
      "SELECT FUNCTION('DATE', FUNCTION('CONVERT_TZ', timestampadd(second, floor(FUNCTION('UNIX_TIMESTAMP', l.createdAt)), datetime 1970-01-01 00:00:00), '+00:00', :tz)) AS day, "
          + "COUNT(l) AS count "
          + "FROM LinkEntity l WHERE l.createdAt >= :since "
          + "GROUP BY FUNCTION('DATE', FUNCTION('CONVERT_TZ', timestampadd(second, floor(FUNCTION('UNIX_TIMESTAMP', l.createdAt)), datetime 1970-01-01 00:00:00), '+00:00', :tz)) "
          + "ORDER BY day")
  List<DailyRow> dailyLinksSince(@Param("since") Instant since, @Param("tz") String tz);

  @Query(
      "SELECT FUNCTION('DATE', FUNCTION('CONVERT_TZ', timestampadd(second, floor(FUNCTION('UNIX_TIMESTAMP', c.clickedAt)), datetime 1970-01-01 00:00:00), '+00:00', :tz)) AS day, "
          + "COUNT(c) AS count "
          + "FROM ClickEventEntity c WHERE c.clickedAt >= :since "
          + "AND c.linkId NOT IN (SELECT s.id FROM LinkEntity s WHERE s.userId = :smokeUserId) "
          + "GROUP BY FUNCTION('DATE', FUNCTION('CONVERT_TZ', timestampadd(second, floor(FUNCTION('UNIX_TIMESTAMP', c.clickedAt)), datetime 1970-01-01 00:00:00), '+00:00', :tz)) "
          + "ORDER BY day")
  List<DailyRow> dailyClicksSince(
      @Param("since") Instant since,
      @Param("tz") String tz,
      @Param("smokeUserId") long smokeUserId);

  @Query(
      value =
          "SELECT u.id AS userId, u.email AS email, COUNT(l) AS count "
              + "FROM LinkEntity l JOIN UserEntity u ON u.id = l.userId "
              + "GROUP BY u.id, u.email ORDER BY count DESC",
      countQuery =
          "SELECT COUNT(DISTINCT u.id) FROM LinkEntity l JOIN UserEntity u ON u.id = l.userId")
  Page<UserStatRow> topUsersByLinks(Pageable pageable);

  @Query(
      value =
          "SELECT u.id AS userId, u.email AS email, CAST(SUM(x.n) AS SIGNED) AS count FROM "
              + HUMANS_PER_LINK
              + "JOIN link l ON l.id = x.link_id JOIN users u ON u.id = l.user_id "
              + "GROUP BY u.id, u.email HAVING SUM(x.n) > 0 ORDER BY count DESC",
      countQuery =
          "SELECT COUNT(DISTINCT u.id) FROM "
              + HUMANS_PER_LINK
              + "JOIN link l ON l.id = x.link_id JOIN users u ON u.id = l.user_id WHERE x.n > 0",
      nativeQuery = true)
  Page<UserStatRow> topUsersByClicks(Pageable pageable);

  @Query(
      value =
          "SELECT l.short_code AS shortCode, CAST(SUM(x.n) AS SIGNED) AS count, "
              + "u.email AS ownerEmail FROM "
              + HUMANS_PER_LINK
              + "JOIN link l ON l.id = x.link_id LEFT JOIN users u ON u.id = l.user_id "
              + "GROUP BY l.short_code, u.email HAVING SUM(x.n) > 0 ORDER BY count DESC",
      countQuery =
          "SELECT COUNT(DISTINCT l.short_code) FROM "
              + HUMANS_PER_LINK
              + "JOIN link l ON l.id = x.link_id WHERE x.n > 0",
      nativeQuery = true)
  Page<LinkStatRow> topLinksByClicks(Pageable pageable);

  @Query(
      "SELECT l.shortCode AS shortCode, l.originalUrl AS originalUrl, "
          + "l.userId AS userId, u.email AS ownerEmail, "
          + "(SELECT COUNT(c) FROM ClickEventEntity c WHERE c.linkId = l.id "
          + "AND c.clickedAt >= "
          + ROLLED_UNTIL_JPQL
          + ") + (SELECT COALESCE(SUM(h.humans + h.bots), 0) FROM ClickHourlyEntity h "
          + "WHERE h.linkId = l.id) AS totalRedirects, "
          + "COALESCE((SELECT MAX(c.clickedAt) FROM ClickEventEntity c WHERE c.linkId = l.id "
          + "AND c.clickedAt >= "
          + ROLLED_UNTIL_JPQL
          + "), (SELECT MAX(h.lastAt) FROM ClickHourlyEntity h WHERE h.linkId = l.id)) "
          + "AS lastRedirectAt "
          + "FROM LinkEntity l LEFT JOIN UserEntity u ON u.id = l.userId "
          + "WHERE l.shortCode IN :shortCodes")
  List<LinkMetricRow> linkMetricRowsByShortCodes(
      @Param("shortCodes") Collection<ShortCode> shortCodes);

  @Query(
      "SELECT l.shortCode AS shortCode, l.originalUrl AS originalUrl, "
          + "u.email AS ownerEmail, l.createdAt AS createdAt "
          + "FROM LinkEntity l LEFT JOIN UserEntity u ON u.id = l.userId "
          + "ORDER BY l.createdAt DESC")
  List<RecentLinkRow> recentLinks(Pageable pageable);

  @Query(
      "SELECT l.shortCode AS shortCode, c.clickedAt AS clickedAt, "
          + "c.countryCode AS countryCode, c.referrerHost AS referrerHost, "
          + "c.deviceClass AS deviceClass "
          + "FROM ClickEventEntity c JOIN LinkEntity l ON l.id = c.linkId "
          + "WHERE l.userId IS NULL OR l.userId <> :smokeUserId "
          + "ORDER BY c.clickedAt DESC")
  List<RecentClickRow> recentClicks(@Param("smokeUserId") long smokeUserId, Pageable pageable);

  @Query(
      "SELECT l.shortCode AS shortCode, COUNT(c) AS count, u.email AS ownerEmail "
          + "FROM ClickEventEntity c JOIN LinkEntity l ON l.id = c.linkId "
          + "LEFT JOIN UserEntity u ON u.id = l.userId "
          + "WHERE c.bot = false AND c.clickedAt >= :since "
          + "GROUP BY l.shortCode, u.email ORDER BY count DESC")
  List<LinkStatRow> topLinksByClicksSince(@Param("since") Instant since, Pageable pageable);
}
