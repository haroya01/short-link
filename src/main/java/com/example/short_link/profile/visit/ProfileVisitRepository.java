package com.example.short_link.profile.visit;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProfileVisitRepository extends JpaRepository<ProfileVisitEntity, Long> {

  long countByProfileUserId(Long profileUserId);

  long countByProfileUserIdAndBotFalse(Long profileUserId);

  long countByProfileUserIdAndVisitedAtAfter(Long profileUserId, Instant after);

  long countByProfileUserIdAndBotFalseAndVisitedAtAfter(Long profileUserId, Instant after);

  @Query(
      "SELECT COUNT(p) FROM ProfileVisitEntity p "
          + "WHERE p.profileUserId = :userId AND p.bot = true")
  long countBotByUserId(@Param("userId") Long userId);

  @Query(
      "SELECT COUNT(DISTINCT p.visitorHash) FROM ProfileVisitEntity p "
          + "WHERE p.profileUserId = :userId AND p.bot = false AND p.visitorHash IS NOT NULL")
  long countUniqueVisitorsByUserId(@Param("userId") Long userId);

  @Query(
      "SELECT MIN(p.visitedAt) FROM ProfileVisitEntity p "
          + "WHERE p.profileUserId = :userId AND p.bot = false")
  Instant findFirstVisitAt(@Param("userId") Long userId);

  @Query(
      "SELECT MAX(p.visitedAt) FROM ProfileVisitEntity p "
          + "WHERE p.profileUserId = :userId AND p.bot = false")
  Instant findLastVisitAt(@Param("userId") Long userId);

  @Query(
      "SELECT FUNCTION('DATE', FUNCTION('CONVERT_TZ', p.visitedAt, '+00:00', :tz)) AS day, "
          + "COUNT(p) AS count "
          + "FROM ProfileVisitEntity p WHERE p.profileUserId = :userId AND p.bot = false "
          + "AND p.visitedAt >= :from "
          + "GROUP BY FUNCTION('DATE', FUNCTION('CONVERT_TZ', p.visitedAt, '+00:00', :tz)) "
          + "ORDER BY day")
  List<DailyVisitRow> findDailyVisits(
      @Param("userId") Long userId, @Param("from") Instant from, @Param("tz") String timezone);

  @Query(
      "SELECT FUNCTION('HOUR', FUNCTION('CONVERT_TZ', p.visitedAt, '+00:00', :tz)) AS hour, "
          + "COUNT(p) AS count "
          + "FROM ProfileVisitEntity p WHERE p.profileUserId = :userId AND p.bot = false "
          + "GROUP BY FUNCTION('HOUR', FUNCTION('CONVERT_TZ', p.visitedAt, '+00:00', :tz)) "
          + "ORDER BY hour")
  List<HourVisitRow> findHourlyVisits(@Param("userId") Long userId, @Param("tz") String timezone);

  @Query(
      "SELECT FUNCTION('DAYOFWEEK', FUNCTION('CONVERT_TZ', p.visitedAt, '+00:00', :tz)) AS dow, "
          + "FUNCTION('HOUR', FUNCTION('CONVERT_TZ', p.visitedAt, '+00:00', :tz)) AS hour, "
          + "COUNT(p) AS count "
          + "FROM ProfileVisitEntity p WHERE p.profileUserId = :userId AND p.bot = false "
          + "GROUP BY dow, hour ORDER BY dow, hour")
  List<HeatmapRow> findHeatmap(@Param("userId") Long userId, @Param("tz") String timezone);

  @Query(
      "SELECT p.countryCode AS country, COUNT(p) AS count "
          + "FROM ProfileVisitEntity p WHERE p.profileUserId = :userId AND p.bot = false "
          + "GROUP BY p.countryCode ORDER BY count DESC")
  List<CountryVisitRow> findCountryVisits(@Param("userId") Long userId, Pageable pageable);

  @Query(
      "SELECT p.deviceClass AS device, COUNT(p) AS count "
          + "FROM ProfileVisitEntity p WHERE p.profileUserId = :userId AND p.bot = false "
          + "GROUP BY p.deviceClass ORDER BY count DESC")
  List<DeviceVisitRow> findDeviceVisits(@Param("userId") Long userId);

  @Query(
      "SELECT p.browserName AS browser, COUNT(p) AS count "
          + "FROM ProfileVisitEntity p WHERE p.profileUserId = :userId AND p.bot = false "
          + "GROUP BY p.browserName ORDER BY count DESC")
  List<BrowserVisitRow> findBrowserVisits(@Param("userId") Long userId, Pageable pageable);

  @Query(
      "SELECT p.referrerHost AS host, COUNT(p) AS count "
          + "FROM ProfileVisitEntity p WHERE p.profileUserId = :userId AND p.bot = false "
          + "AND p.referrerHost IS NOT NULL "
          + "GROUP BY p.referrerHost ORDER BY count DESC")
  List<ReferrerHostVisitRow> findReferrerHostVisits(
      @Param("userId") Long userId, Pageable pageable);

  @Query(
      "SELECT p.sourceChannel AS source, COUNT(p) AS count "
          + "FROM ProfileVisitEntity p WHERE p.profileUserId = :userId AND p.bot = false "
          + "AND p.sourceChannel IS NOT NULL "
          + "GROUP BY p.sourceChannel ORDER BY count DESC")
  List<SourceChannelVisitRow> findSourceChannelVisits(
      @Param("userId") Long userId, Pageable pageable);

  @Query(
      "SELECT p.utmCampaign AS campaign, COUNT(p) AS count "
          + "FROM ProfileVisitEntity p WHERE p.profileUserId = :userId AND p.bot = false "
          + "AND p.utmCampaign IS NOT NULL "
          + "GROUP BY p.utmCampaign ORDER BY count DESC")
  List<UtmCampaignVisitRow> findUtmCampaignVisits(@Param("userId") Long userId, Pageable pageable);

  @Query(
      "SELECT p.utmSource AS source, COUNT(p) AS count "
          + "FROM ProfileVisitEntity p WHERE p.profileUserId = :userId AND p.bot = false "
          + "AND p.utmSource IS NOT NULL "
          + "GROUP BY p.utmSource ORDER BY count DESC")
  List<UtmSourceVisitRow> findUtmSourceVisits(@Param("userId") Long userId, Pageable pageable);

  interface DailyVisitRow {
    LocalDate getDay();

    Long getCount();
  }

  interface HourVisitRow {
    Integer getHour();

    Long getCount();
  }

  interface HeatmapRow {
    Integer getDow();

    Integer getHour();

    Long getCount();
  }

  interface CountryVisitRow {
    String getCountry();

    Long getCount();
  }

  interface DeviceVisitRow {
    String getDevice();

    Long getCount();
  }

  interface BrowserVisitRow {
    String getBrowser();

    Long getCount();
  }

  interface ReferrerHostVisitRow {
    String getHost();

    Long getCount();
  }

  interface SourceChannelVisitRow {
    String getSource();

    Long getCount();
  }

  interface UtmCampaignVisitRow {
    String getCampaign();

    Long getCount();
  }

  interface UtmSourceVisitRow {
    String getSource();

    Long getCount();
  }
}
