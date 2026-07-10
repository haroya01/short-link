package com.example.short_link.admin.infrastructure.persistence;

import com.example.short_link.admin.domain.repository.AdminMetricsRepository.DailyRow;
import com.example.short_link.admin.domain.repository.AdminMetricsRepository.LinkMetricRow;
import com.example.short_link.admin.domain.repository.AdminMetricsRepository.LinkStatRow;
import com.example.short_link.admin.domain.repository.AdminMetricsRepository.RecentClickRow;
import com.example.short_link.admin.domain.repository.AdminMetricsRepository.RecentLinkRow;
import com.example.short_link.admin.domain.repository.AdminMetricsRepository.UserStatRow;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.stats.domain.ClickEventEntity;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaAdminMetricsRepository extends JpaRepository<ClickEventEntity, Long> {

  @Query("SELECT COUNT(c) FROM ClickEventEntity c")
  long totalClicks();

  @Query("SELECT COUNT(c) FROM ClickEventEntity c WHERE c.clickedAt >= :since")
  long clicksSince(@Param("since") Instant since);

  @Query(
      "SELECT COUNT(l) FROM LinkEntity l WHERE NOT EXISTS "
          + "(SELECT 1 FROM ClickEventEntity c WHERE c.linkId = l.id)")
  long linksWithoutClicks();

  @Query(
      "SELECT FUNCTION('DATE', FUNCTION('CONVERT_TZ', u.createdAt, '+00:00', :tz)) AS day, "
          + "COUNT(u) AS count "
          + "FROM UserEntity u WHERE u.createdAt >= :since "
          + "GROUP BY FUNCTION('DATE', FUNCTION('CONVERT_TZ', u.createdAt, '+00:00', :tz)) "
          + "ORDER BY day")
  List<DailyRow> dailySignupsSince(@Param("since") Instant since, @Param("tz") String tz);

  @Query(
      "SELECT FUNCTION('DATE', FUNCTION('CONVERT_TZ', l.createdAt, '+00:00', :tz)) AS day, "
          + "COUNT(l) AS count "
          + "FROM LinkEntity l WHERE l.createdAt >= :since "
          + "GROUP BY FUNCTION('DATE', FUNCTION('CONVERT_TZ', l.createdAt, '+00:00', :tz)) "
          + "ORDER BY day")
  List<DailyRow> dailyLinksSince(@Param("since") Instant since, @Param("tz") String tz);

  @Query(
      "SELECT FUNCTION('DATE', FUNCTION('CONVERT_TZ', c.clickedAt, '+00:00', :tz)) AS day, "
          + "COUNT(c) AS count "
          + "FROM ClickEventEntity c WHERE c.clickedAt >= :since "
          + "GROUP BY FUNCTION('DATE', FUNCTION('CONVERT_TZ', c.clickedAt, '+00:00', :tz)) "
          + "ORDER BY day")
  List<DailyRow> dailyClicksSince(@Param("since") Instant since, @Param("tz") String tz);

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
          "SELECT u.id AS userId, u.email AS email, COUNT(c) AS count "
              + "FROM ClickEventEntity c JOIN LinkEntity l ON l.id = c.linkId "
              + "JOIN UserEntity u ON u.id = l.userId "
              + "WHERE c.bot = false "
              + "GROUP BY u.id, u.email ORDER BY count DESC",
      countQuery =
          "SELECT COUNT(DISTINCT u.id) FROM ClickEventEntity c "
              + "JOIN LinkEntity l ON l.id = c.linkId "
              + "JOIN UserEntity u ON u.id = l.userId "
              + "WHERE c.bot = false")
  Page<UserStatRow> topUsersByClicks(Pageable pageable);

  @Query(
      value =
          "SELECT l.shortCode AS shortCode, COUNT(c) AS count, u.email AS ownerEmail "
              + "FROM ClickEventEntity c JOIN LinkEntity l ON l.id = c.linkId "
              + "LEFT JOIN UserEntity u ON u.id = l.userId "
              + "WHERE c.bot = false "
              + "GROUP BY l.shortCode, u.email ORDER BY count DESC",
      countQuery =
          "SELECT COUNT(DISTINCT l.shortCode) FROM ClickEventEntity c "
              + "JOIN LinkEntity l ON l.id = c.linkId "
              + "WHERE c.bot = false")
  Page<LinkStatRow> topLinksByClicks(Pageable pageable);

  @Query(
      "SELECT l.shortCode AS shortCode, l.originalUrl AS originalUrl, "
          + "l.userId AS userId, u.email AS ownerEmail, "
          + "(SELECT COUNT(c) FROM ClickEventEntity c WHERE c.linkId = l.id) AS totalRedirects, "
          + "(SELECT MAX(c.clickedAt) FROM ClickEventEntity c WHERE c.linkId = l.id) AS lastRedirectAt "
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
          + "ORDER BY c.clickedAt DESC")
  List<RecentClickRow> recentClicks(Pageable pageable);

  @Query(
      "SELECT l.shortCode AS shortCode, COUNT(c) AS count, u.email AS ownerEmail "
          + "FROM ClickEventEntity c JOIN LinkEntity l ON l.id = c.linkId "
          + "LEFT JOIN UserEntity u ON u.id = l.userId "
          + "WHERE c.bot = false AND c.clickedAt >= :since "
          + "GROUP BY l.shortCode, u.email ORDER BY count DESC")
  List<LinkStatRow> topLinksByClicksSince(@Param("since") Instant since, Pageable pageable);
}
