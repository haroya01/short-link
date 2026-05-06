package com.example.short_link.link.domain;

import java.time.Instant;
import java.util.List;
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
      "SELECT c.linkId AS linkId, COUNT(c) AS cnt FROM ClickEventEntity c "
          + "WHERE c.linkId IN :ids GROUP BY c.linkId")
  List<LinkClickCount> countsByLinkIds(@Param("ids") List<Long> ids);

  @Query(
      "SELECT FUNCTION('DATE', c.clickedAt) AS day, COUNT(c) AS cnt "
          + "FROM ClickEventEntity c WHERE c.linkId = :linkId AND c.bot = false "
          + "AND c.clickedAt >= :from "
          + "GROUP BY FUNCTION('DATE', c.clickedAt) ORDER BY day")
  List<DailyClickRow> findDailyClicks(@Param("linkId") Long linkId, @Param("from") Instant from);

  @Query(
      "SELECT FUNCTION('HOUR', c.clickedAt) AS hour, COUNT(c) AS cnt "
          + "FROM ClickEventEntity c WHERE c.linkId = :linkId AND c.bot = false "
          + "GROUP BY FUNCTION('HOUR', c.clickedAt) ORDER BY hour")
  List<HourClickRow> findHourlyClicks(@Param("linkId") Long linkId);

  @Query(
      "SELECT FUNCTION('DAYOFWEEK', c.clickedAt) AS dow, COUNT(c) AS cnt "
          + "FROM ClickEventEntity c WHERE c.linkId = :linkId AND c.bot = false "
          + "GROUP BY FUNCTION('DAYOFWEEK', c.clickedAt) ORDER BY dow")
  List<DayOfWeekClickRow> findDayOfWeekClicks(@Param("linkId") Long linkId);

  @Query(
      "SELECT c.referrer AS referrer, COUNT(c) AS cnt "
          + "FROM ClickEventEntity c WHERE c.linkId = :linkId AND c.bot = false "
          + "GROUP BY c.referrer ORDER BY cnt DESC")
  List<ReferrerClickRow> findReferrerClicks(@Param("linkId") Long linkId);

  @Query(
      "SELECT c.deviceClass AS device, COUNT(c) AS cnt "
          + "FROM ClickEventEntity c WHERE c.linkId = :linkId AND c.bot = false "
          + "GROUP BY c.deviceClass ORDER BY cnt DESC")
  List<DeviceClickRow> findDeviceClicks(@Param("linkId") Long linkId);

  @Query(
      "SELECT c.osName AS os, COUNT(c) AS cnt "
          + "FROM ClickEventEntity c WHERE c.linkId = :linkId AND c.bot = false "
          + "GROUP BY c.osName ORDER BY cnt DESC")
  List<OsClickRow> findOsClicks(@Param("linkId") Long linkId);

  @Query(
      "SELECT c.browserName AS browser, COUNT(c) AS cnt "
          + "FROM ClickEventEntity c WHERE c.linkId = :linkId AND c.bot = false "
          + "GROUP BY c.browserName ORDER BY cnt DESC")
  List<BrowserClickRow> findBrowserClicks(@Param("linkId") Long linkId);

  @Query(
      "SELECT c.utmCampaign AS campaign, COUNT(c) AS cnt "
          + "FROM ClickEventEntity c WHERE c.linkId = :linkId AND c.bot = false "
          + "AND c.utmCampaign IS NOT NULL "
          + "GROUP BY c.utmCampaign ORDER BY cnt DESC")
  List<UtmCampaignClickRow> findUtmCampaignClicks(@Param("linkId") Long linkId);

  interface LinkClickCount {
    Long getLinkId();

    Long getCnt();
  }

  interface DailyClickRow {
    java.sql.Date getDay();

    Long getCnt();
  }

  interface HourClickRow {
    Integer getHour();

    Long getCnt();
  }

  interface DayOfWeekClickRow {
    Integer getDow();

    Long getCnt();
  }

  interface ReferrerClickRow {
    String getReferrer();

    Long getCnt();
  }

  interface DeviceClickRow {
    String getDevice();

    Long getCnt();
  }

  interface OsClickRow {
    String getOs();

    Long getCnt();
  }

  interface BrowserClickRow {
    String getBrowser();

    Long getCnt();
  }

  interface UtmCampaignClickRow {
    String getCampaign();

    Long getCnt();
  }
}
