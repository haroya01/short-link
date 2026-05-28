package com.example.short_link.profile.infrastructure.persistence;

import com.example.short_link.link.stats.domain.repository.projection.ClickProjections;
import com.example.short_link.profile.domain.visit.ProfileVisitEntity;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaProfileVisitRepository extends JpaRepository<ProfileVisitEntity, Long> {

  long countByProfileUserId(Long profileUserId);

  long countByProfileUserIdAndBotFalse(Long profileUserId);

  long countByProfileUserIdAndBotFalseAndVisitedAtAfter(Long profileUserId, Instant after);

  @Query("SELECT COUNT(p) FROM ProfileVisitEntity p WHERE p.profileUserId = :uid AND p.bot = true")
  long countBotByProfileUserId(@Param("uid") Long uid);

  @Query(
      "SELECT COUNT(DISTINCT p.visitorHash) FROM ProfileVisitEntity p "
          + "WHERE p.profileUserId = :uid AND p.bot = false AND p.visitorHash IS NOT NULL")
  long countUniqueVisitorsByProfileUserId(@Param("uid") Long uid);

  @Query(
      "SELECT MIN(p.visitedAt) FROM ProfileVisitEntity p "
          + "WHERE p.profileUserId = :uid AND p.bot = false")
  Instant findFirstVisitAt(@Param("uid") Long uid);

  @Query(
      "SELECT MAX(p.visitedAt) FROM ProfileVisitEntity p "
          + "WHERE p.profileUserId = :uid AND p.bot = false")
  Instant findLastVisitAt(@Param("uid") Long uid);

  @Query(
      "SELECT FUNCTION('DATE', FUNCTION('CONVERT_TZ', p.visitedAt, '+00:00', :tz)) AS day, "
          + "COUNT(p) AS count "
          + "FROM ProfileVisitEntity p WHERE p.profileUserId = :uid AND p.bot = false "
          + "AND p.visitedAt >= :from "
          + "GROUP BY FUNCTION('DATE', FUNCTION('CONVERT_TZ', p.visitedAt, '+00:00', :tz)) "
          + "ORDER BY day")
  List<ClickProjections.DailyClickRow> findDailyVisits(
      @Param("uid") Long uid, @Param("from") Instant from, @Param("tz") String tz);

  @Query(
      "SELECT FUNCTION('HOUR', FUNCTION('CONVERT_TZ', p.visitedAt, '+00:00', :tz)) AS hour, "
          + "COUNT(p) AS count "
          + "FROM ProfileVisitEntity p WHERE p.profileUserId = :uid AND p.bot = false "
          + "GROUP BY FUNCTION('HOUR', FUNCTION('CONVERT_TZ', p.visitedAt, '+00:00', :tz)) "
          + "ORDER BY hour")
  List<ClickProjections.HourClickRow> findHourlyVisits(
      @Param("uid") Long uid, @Param("tz") String tz);

  @Query(
      "SELECT FUNCTION('DAYOFWEEK', FUNCTION('CONVERT_TZ', p.visitedAt, '+00:00', :tz)) AS dow, "
          + "FUNCTION('HOUR', FUNCTION('CONVERT_TZ', p.visitedAt, '+00:00', :tz)) AS hour, "
          + "COUNT(p) AS count "
          + "FROM ProfileVisitEntity p WHERE p.profileUserId = :uid AND p.bot = false "
          + "GROUP BY dow, hour ORDER BY dow, hour")
  List<ClickProjections.HeatmapRow> findHeatmap(@Param("uid") Long uid, @Param("tz") String tz);

  @Query(
      "SELECT p.countryCode AS country, COUNT(p) AS count "
          + "FROM ProfileVisitEntity p WHERE p.profileUserId = :uid AND p.bot = false "
          + "GROUP BY p.countryCode ORDER BY count DESC")
  List<ClickProjections.CountryClickRow> findCountryVisits(
      @Param("uid") Long uid, Pageable pageable);

  @Query(
      "SELECT p.deviceClass AS device, COUNT(p) AS count "
          + "FROM ProfileVisitEntity p WHERE p.profileUserId = :uid AND p.bot = false "
          + "GROUP BY p.deviceClass ORDER BY count DESC")
  List<ClickProjections.DeviceClickRow> findDeviceVisits(@Param("uid") Long uid);

  @Query(
      "SELECT p.browserName AS browser, COUNT(p) AS count "
          + "FROM ProfileVisitEntity p WHERE p.profileUserId = :uid AND p.bot = false "
          + "GROUP BY p.browserName ORDER BY count DESC")
  List<ClickProjections.BrowserClickRow> findBrowserVisits(
      @Param("uid") Long uid, Pageable pageable);

  @Query(
      "SELECT p.referrerHost AS host, COUNT(p) AS count "
          + "FROM ProfileVisitEntity p WHERE p.profileUserId = :uid AND p.bot = false "
          + "AND p.referrerHost IS NOT NULL "
          + "GROUP BY p.referrerHost ORDER BY count DESC")
  List<ClickProjections.ReferrerHostClickRow> findReferrerHostVisits(
      @Param("uid") Long uid, Pageable pageable);

  @Query(
      "SELECT p.sourceChannel AS source, COUNT(p) AS count "
          + "FROM ProfileVisitEntity p WHERE p.profileUserId = :uid AND p.bot = false "
          + "AND p.sourceChannel IS NOT NULL "
          + "GROUP BY p.sourceChannel ORDER BY count DESC")
  List<ClickProjections.SourceChannelClickRow> findSourceChannelVisits(
      @Param("uid") Long uid, Pageable pageable);

  @Query(
      "SELECT p.utmCampaign AS campaign, COUNT(p) AS count "
          + "FROM ProfileVisitEntity p WHERE p.profileUserId = :uid AND p.bot = false "
          + "AND p.utmCampaign IS NOT NULL "
          + "GROUP BY p.utmCampaign ORDER BY count DESC")
  List<ClickProjections.UtmCampaignClickRow> findUtmCampaignVisits(
      @Param("uid") Long uid, Pageable pageable);

  @Query(
      "SELECT p.utmSource AS source, COUNT(p) AS count "
          + "FROM ProfileVisitEntity p WHERE p.profileUserId = :uid AND p.bot = false "
          + "AND p.utmSource IS NOT NULL "
          + "GROUP BY p.utmSource ORDER BY count DESC")
  List<ClickProjections.UtmSourceClickRow> findUtmSourceVisits(
      @Param("uid") Long uid, Pageable pageable);
}
