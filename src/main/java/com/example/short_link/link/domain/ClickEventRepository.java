package com.example.short_link.link.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClickEventRepository extends JpaRepository<ClickEventEntity, Long> {

  long countByLinkId(Long linkId);

  @Query("SELECT COUNT(c) FROM ClickEventEntity c WHERE c.linkId = :linkId AND c.bot = false")
  long countHumanByLinkId(@Param("linkId") Long linkId);

  @Query("SELECT COUNT(c) FROM ClickEventEntity c WHERE c.linkId = :linkId AND c.bot = true")
  long countBotByLinkId(@Param("linkId") Long linkId);

  @Query(
      "SELECT COUNT(c) FROM ClickEventEntity c "
          + "WHERE c.linkId = :linkId AND c.bot = false AND c.referrer IS NULL")
  long countDirectByLinkId(@Param("linkId") Long linkId);

  @Query(
      "SELECT c.linkId AS linkId, COUNT(c) AS count FROM ClickEventEntity c "
          + "WHERE c.linkId IN :ids GROUP BY c.linkId")
  List<LinkClickCount> countsByLinkIds(@Param("ids") List<Long> ids);

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
      "SELECT c.referrer AS referrer, COUNT(c) AS count "
          + "FROM ClickEventEntity c WHERE c.linkId = :linkId AND c.bot = false "
          + "AND c.referrer IS NOT NULL "
          + "GROUP BY c.referrer ORDER BY count DESC")
  List<ReferrerClickRow> findReferrerClicks(@Param("linkId") Long linkId, Pageable pageable);

  @Query(
      "SELECT c.deviceClass AS device, COUNT(c) AS count "
          + "FROM ClickEventEntity c WHERE c.linkId = :linkId AND c.bot = false "
          + "GROUP BY c.deviceClass ORDER BY count DESC")
  List<DeviceClickRow> findDeviceClicks(@Param("linkId") Long linkId);

  @Query(
      "SELECT c.osName AS os, COUNT(c) AS count "
          + "FROM ClickEventEntity c WHERE c.linkId = :linkId AND c.bot = false "
          + "GROUP BY c.osName ORDER BY count DESC")
  List<OsClickRow> findOsClicks(@Param("linkId") Long linkId, Pageable pageable);

  @Query(
      "SELECT c.browserName AS browser, COUNT(c) AS count "
          + "FROM ClickEventEntity c WHERE c.linkId = :linkId AND c.bot = false "
          + "GROUP BY c.browserName ORDER BY count DESC")
  List<BrowserClickRow> findBrowserClicks(@Param("linkId") Long linkId, Pageable pageable);

  @Query(
      "SELECT c.utmCampaign AS campaign, COUNT(c) AS count "
          + "FROM ClickEventEntity c WHERE c.linkId = :linkId AND c.bot = false "
          + "AND c.utmCampaign IS NOT NULL "
          + "GROUP BY c.utmCampaign ORDER BY count DESC")
  List<UtmCampaignClickRow> findUtmCampaignClicks(@Param("linkId") Long linkId, Pageable pageable);

  @Query(
      "SELECT c.countryCode AS country, COUNT(c) AS count "
          + "FROM ClickEventEntity c WHERE c.linkId = :linkId AND c.bot = false "
          + "GROUP BY c.countryCode ORDER BY count DESC")
  List<CountryClickRow> findCountryClicks(@Param("linkId") Long linkId, Pageable pageable);

  interface LinkClickCount {
    Long getLinkId();

    Long getCount();
  }

  interface DailyClickRow {
    LocalDate getDay();

    Long getCount();
  }

  interface HourClickRow {
    Integer getHour();

    Long getCount();
  }

  interface DayOfWeekClickRow {
    Integer getDow();

    Long getCount();
  }

  interface ReferrerClickRow {
    String getReferrer();

    Long getCount();
  }

  interface DeviceClickRow {
    String getDevice();

    Long getCount();
  }

  interface OsClickRow {
    String getOs();

    Long getCount();
  }

  interface BrowserClickRow {
    String getBrowser();

    Long getCount();
  }

  interface UtmCampaignClickRow {
    String getCampaign();

    Long getCount();
  }

  interface CountryClickRow {
    String getCountry();

    Long getCount();
  }
}
