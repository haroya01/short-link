package com.example.short_link.link.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClickEventRepository extends JpaRepository<ClickEventEntity, Long> {

  long countByLinkId(Long linkId);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("DELETE FROM ClickEventEntity c WHERE c.linkId IN :linkIds")
  int deleteByLinkIds(@Param("linkIds") Collection<Long> linkIds);

  List<ClickEventEntity> findAllByLinkIdInOrderByClickedAtAsc(Collection<Long> linkIds);

  @Query("SELECT COUNT(c) FROM ClickEventEntity c WHERE c.linkId = :linkId AND c.bot = false")
  long countHumanByLinkId(@Param("linkId") Long linkId);

  @Query("SELECT COUNT(c) FROM ClickEventEntity c WHERE c.linkId = :linkId AND c.bot = true")
  long countBotByLinkId(@Param("linkId") Long linkId);

  @Query(
      "SELECT COUNT(c) FROM ClickEventEntity c "
          + "WHERE c.linkId = :linkId AND c.bot = false AND c.referrer IS NULL")
  long countDirectByLinkId(@Param("linkId") Long linkId);

  /**
   * Clicks attributed to "this link was clicked from the owner's public profile page". Every LINK
   * block on /u/&lt;handle&gt; appends {@code ?src=profile-<handle>} when sending visitors to the
   * short URL, so we count any {@code sourceChannel} starting with the {@code profile-} prefix. The
   * owner-facing stats page surfaces this as a top-level KPI so they can split "from my profile"
   * from external sharing without scrolling to the channel breakdown.
   */
  @Query(
      "SELECT COUNT(c) FROM ClickEventEntity c "
          + "WHERE c.linkId = :linkId AND c.bot = false "
          + "AND c.sourceChannel LIKE 'profile-%'")
  long countProfileChannelByLinkId(@Param("linkId") Long linkId);

  @Query(
      "SELECT COUNT(DISTINCT c.visitorHash) FROM ClickEventEntity c "
          + "WHERE c.linkId = :linkId AND c.bot = false AND c.visitorHash IS NOT NULL")
  long countUniqueVisitorsByLinkId(@Param("linkId") Long linkId);

  @Query(
      "SELECT MIN(c.clickedAt) FROM ClickEventEntity c "
          + "WHERE c.linkId = :linkId AND c.bot = false")
  Instant findFirstClickAt(@Param("linkId") Long linkId);

  @Query(
      "SELECT MAX(c.clickedAt) FROM ClickEventEntity c "
          + "WHERE c.linkId = :linkId AND c.bot = false")
  Instant findLastClickAt(@Param("linkId") Long linkId);

  /**
   * OG preview hits are persisted with bot=true and a "preview:..." prefixed bot_name (set by
   * {@code ClickRecorder.recordPreview}) so the stats UI can split social/messenger previews out of
   * real bot traffic regardless of yauaa coverage.
   */
  @Query(
      "SELECT COUNT(c) FROM ClickEventEntity c "
          + "WHERE c.linkId = :linkId AND c.bot = true AND c.botName LIKE 'preview:%'")
  long countPreviewByLinkId(@Param("linkId") Long linkId);

  @Query(
      "SELECT COUNT(c) FROM ClickEventEntity c "
          + "WHERE c.linkId = :linkId AND c.bot = false AND c.clickedAt >= :since")
  long countSinceByLinkId(@Param("linkId") Long linkId, @Param("since") Instant since);

  @Query(
      "SELECT c.linkId AS linkId, COUNT(c) AS count FROM ClickEventEntity c "
          + "WHERE c.linkId IN :ids GROUP BY c.linkId")
  List<LinkClickCount> countsByLinkIds(@Param("ids") List<Long> ids);

  @Query(
      "SELECT c.linkId AS linkId, COUNT(c) AS count FROM ClickEventEntity c "
          + "WHERE c.linkId IN :ids AND c.bot = false AND c.clickedAt >= :since "
          + "GROUP BY c.linkId")
  List<LinkClickCount> countsByLinkIdsSince(
      @Param("ids") List<Long> ids, @Param("since") Instant since);

  @Query(
      "SELECT c.linkId AS linkId, COUNT(c) AS count FROM ClickEventEntity c "
          + "WHERE c.linkId IN :ids AND c.bot = false AND c.clickedAt < :before "
          + "GROUP BY c.linkId")
  List<LinkClickCount> countsByLinkIdsBefore(
      @Param("ids") List<Long> ids, @Param("before") Instant before);

  @Query(
      "SELECT MAX(c.clickedAt) FROM ClickEventEntity c "
          + "WHERE c.linkId IN :ids AND c.bot = false AND c.clickedAt < :before")
  Instant findLastClickBeforeByLinkIds(
      @Param("ids") List<Long> ids, @Param("before") Instant before);

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
      "SELECT FUNCTION('DAYOFWEEK', FUNCTION('CONVERT_TZ', c.clickedAt, '+00:00', :tz)) AS dow, "
          + "FUNCTION('HOUR', FUNCTION('CONVERT_TZ', c.clickedAt, '+00:00', :tz)) AS hour, "
          + "COUNT(c) AS count "
          + "FROM ClickEventEntity c WHERE c.linkId = :linkId AND c.bot = false "
          + "GROUP BY dow, hour ORDER BY dow, hour")
  List<HeatmapRow> findHeatmap(@Param("linkId") Long linkId, @Param("tz") String timezone);

  @Query(
      "SELECT c.referrer AS referrer, COUNT(c) AS count "
          + "FROM ClickEventEntity c WHERE c.linkId = :linkId AND c.bot = false "
          + "AND c.referrer IS NOT NULL "
          + "GROUP BY c.referrer ORDER BY count DESC")
  List<ReferrerClickRow> findReferrerClicks(@Param("linkId") Long linkId, Pageable pageable);

  @Query(
      "SELECT c.referrerHost AS host, COUNT(c) AS count "
          + "FROM ClickEventEntity c WHERE c.linkId = :linkId AND c.bot = false "
          + "AND c.referrerHost IS NOT NULL "
          + "GROUP BY c.referrerHost ORDER BY count DESC")
  List<ReferrerHostClickRow> findReferrerHostClicks(
      @Param("linkId") Long linkId, Pageable pageable);

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
      "SELECT c.botName AS bot, COUNT(c) AS count "
          + "FROM ClickEventEntity c WHERE c.linkId = :linkId AND c.bot = true "
          + "GROUP BY c.botName ORDER BY count DESC")
  List<BotClickRow> findBotClicks(@Param("linkId") Long linkId, Pageable pageable);

  @Query(
      "SELECT c.utmCampaign AS campaign, COUNT(c) AS count "
          + "FROM ClickEventEntity c WHERE c.linkId = :linkId AND c.bot = false "
          + "AND c.utmCampaign IS NOT NULL "
          + "GROUP BY c.utmCampaign ORDER BY count DESC")
  List<UtmCampaignClickRow> findUtmCampaignClicks(@Param("linkId") Long linkId, Pageable pageable);

  @Query(
      "SELECT c.utmSource AS source, COUNT(c) AS count "
          + "FROM ClickEventEntity c WHERE c.linkId = :linkId AND c.bot = false "
          + "AND c.utmSource IS NOT NULL "
          + "GROUP BY c.utmSource ORDER BY count DESC")
  List<UtmSourceClickRow> findUtmSourceClicks(@Param("linkId") Long linkId, Pageable pageable);

  @Query(
      "SELECT c.utmMedium AS medium, COUNT(c) AS count "
          + "FROM ClickEventEntity c WHERE c.linkId = :linkId AND c.bot = false "
          + "AND c.utmMedium IS NOT NULL "
          + "GROUP BY c.utmMedium ORDER BY count DESC")
  List<UtmMediumClickRow> findUtmMediumClicks(@Param("linkId") Long linkId, Pageable pageable);

  @Query(
      "SELECT c.utmContent AS content, COUNT(c) AS count "
          + "FROM ClickEventEntity c WHERE c.linkId = :linkId AND c.bot = false "
          + "AND c.utmContent IS NOT NULL "
          + "GROUP BY c.utmContent ORDER BY count DESC")
  List<UtmContentClickRow> findUtmContentClicks(@Param("linkId") Long linkId, Pageable pageable);

  @Query(
      "SELECT c.sourceChannel AS source, COUNT(c) AS count "
          + "FROM ClickEventEntity c WHERE c.linkId = :linkId AND c.bot = false "
          + "AND c.sourceChannel IS NOT NULL "
          + "GROUP BY c.sourceChannel ORDER BY count DESC")
  List<SourceChannelClickRow> findSourceChannelClicks(
      @Param("linkId") Long linkId, Pageable pageable);

  @Query(
      "SELECT c.destinationId AS destinationId, COUNT(c) AS count "
          + "FROM ClickEventEntity c WHERE c.linkId = :linkId AND c.bot = false "
          + "GROUP BY c.destinationId ORDER BY count DESC")
  List<DestinationClickRow> findDestinationClicks(@Param("linkId") Long linkId);

  @Query(
      "SELECT c.countryCode AS country, COUNT(c) AS count "
          + "FROM ClickEventEntity c WHERE c.linkId = :linkId AND c.bot = false "
          + "GROUP BY c.countryCode ORDER BY count DESC")
  List<CountryClickRow> findCountryClicks(@Param("linkId") Long linkId, Pageable pageable);

  @Query(
      "SELECT c.regionName AS region, COUNT(c) AS count "
          + "FROM ClickEventEntity c WHERE c.linkId = :linkId AND c.bot = false "
          + "AND c.regionName IS NOT NULL "
          + "GROUP BY c.regionName ORDER BY count DESC")
  List<RegionClickRow> findRegionClicks(@Param("linkId") Long linkId, Pageable pageable);

  @Query(
      "SELECT c.cityName AS city, COUNT(c) AS count "
          + "FROM ClickEventEntity c WHERE c.linkId = :linkId AND c.bot = false "
          + "AND c.cityName IS NOT NULL "
          + "GROUP BY c.cityName ORDER BY count DESC")
  List<CityClickRow> findCityClicks(@Param("linkId") Long linkId, Pageable pageable);

  @Query(
      "SELECT c.language AS language, COUNT(c) AS count "
          + "FROM ClickEventEntity c WHERE c.linkId = :linkId AND c.bot = false "
          + "AND c.language IS NOT NULL "
          + "GROUP BY c.language ORDER BY count DESC")
  List<LanguageClickRow> findLanguageClicks(@Param("linkId") Long linkId, Pageable pageable);

  @Query(
      "SELECT c.asn AS asn, c.asnOrg AS organization, COUNT(c) AS count "
          + "FROM ClickEventEntity c WHERE c.linkId = :linkId "
          + "AND c.asnOrg IS NOT NULL "
          + "GROUP BY c.asn, c.asnOrg ORDER BY count DESC")
  List<AsnClickRow> findAsnClicks(@Param("linkId") Long linkId, Pageable pageable);

  @Query(
      "SELECT COUNT(c) FROM ClickEventEntity c "
          + "WHERE c.linkId = :linkId AND c.botName LIKE 'datacenter:%'")
  long countDatacenterClicks(@Param("linkId") Long linkId);

  @Query(
      value =
          "SELECT SUM(CASE WHEN cnt = 1 THEN 1 ELSE 0 END) AS newCount, "
              + "SUM(CASE WHEN cnt >= 2 THEN 1 ELSE 0 END) AS returningCount "
              + "FROM (SELECT visitor_hash, COUNT(*) AS cnt FROM click_event "
              + "WHERE link_id = :linkId AND visitor_hash IS NOT NULL AND is_bot = 0 "
              + "GROUP BY visitor_hash) t",
      nativeQuery = true)
  ReturnRateRow findReturnRate(@Param("linkId") Long linkId);

  @Query(
      value =
          "SELECT TIMESTAMPDIFF(DAY, l.created_at, c.clicked_at) AS day, COUNT(*) AS count "
              + "FROM click_event c JOIN link l ON c.link_id = l.id "
              + "WHERE l.id = :linkId AND c.is_bot = 0 "
              + "AND TIMESTAMPDIFF(DAY, l.created_at, c.clicked_at) BETWEEN 0 AND :maxDay "
              + "GROUP BY day ORDER BY day",
      nativeQuery = true)
  List<DayClickRow> findLifecycleClicks(@Param("linkId") Long linkId, @Param("maxDay") int maxDay);

  @Query(
      "SELECT c FROM ClickEventEntity c WHERE c.linkId = :linkId AND c.id < :cursorId "
          + "ORDER BY c.id DESC")
  List<ClickEventEntity> findEventsByLinkIdBefore(
      @Param("linkId") Long linkId, @Param("cursorId") Long cursorId, Pageable pageable);

  @Query("SELECT c FROM ClickEventEntity c WHERE c.linkId = :linkId ORDER BY c.id DESC")
  List<ClickEventEntity> findEventsByLinkIdLatest(@Param("linkId") Long linkId, Pageable pageable);

  @Query(
      "SELECT COUNT(c) FROM ClickEventEntity c "
          + "WHERE c.linkId IN (SELECT l.id FROM LinkEntity l WHERE l.userId = :userId) "
          + "AND c.bot = false "
          + "AND c.clickedAt >= :from AND c.clickedAt < :to")
  long countHumanByUserIdAndRange(
      @Param("userId") Long userId, @Param("from") Instant from, @Param("to") Instant to);

  @Query(
      "SELECT COUNT(c) FROM ClickEventEntity c "
          + "WHERE c.linkId IN (SELECT l.id FROM LinkEntity l WHERE l.userId = :userId) "
          + "AND c.clickedAt >= :from AND c.clickedAt < :to")
  long countByUserIdAndRange(
      @Param("userId") Long userId, @Param("from") Instant from, @Param("to") Instant to);

  @Query(
      "SELECT c.linkId AS linkId, COUNT(c) AS count FROM ClickEventEntity c "
          + "WHERE c.linkId IN (SELECT l.id FROM LinkEntity l WHERE l.userId = :userId) "
          + "AND c.bot = false "
          + "AND c.clickedAt >= :from AND c.clickedAt < :to "
          + "GROUP BY c.linkId ORDER BY count DESC")
  List<LinkClickCount> findTopLinksByUserIdAndRange(
      @Param("userId") Long userId,
      @Param("from") Instant from,
      @Param("to") Instant to,
      Pageable pageable);

  @Query(
      "SELECT c.utmSource AS source, COUNT(c) AS count FROM ClickEventEntity c "
          + "WHERE c.linkId = :linkId AND c.bot = false "
          + "AND c.utmSource IS NOT NULL "
          + "AND c.clickedAt >= :from AND c.clickedAt < :to "
          + "GROUP BY c.utmSource ORDER BY count DESC")
  List<UtmSourceClickRow> findTopUtmSourcesByLinkIdAndRange(
      @Param("linkId") Long linkId,
      @Param("from") Instant from,
      @Param("to") Instant to,
      Pageable pageable);

  @Query(
      "SELECT c.linkId AS linkId, FUNCTION('DATE', c.clickedAt) AS day, COUNT(c) AS count "
          + "FROM ClickEventEntity c "
          + "WHERE c.linkId IN :ids AND c.bot = false AND c.clickedAt >= :from "
          + "GROUP BY c.linkId, FUNCTION('DATE', c.clickedAt)")
  List<DailyClicksByLinkRow> findDailyClicksByLinkIdsSince(
      @Param("ids") List<Long> ids, @Param("from") Instant from);

  @Query(
      "SELECT FUNCTION('DAYOFWEEK', FUNCTION('CONVERT_TZ', c.clickedAt, '+00:00', :tz)) AS dow, "
          + "FUNCTION('HOUR', FUNCTION('CONVERT_TZ', c.clickedAt, '+00:00', :tz)) AS hour, "
          + "COUNT(c) AS count "
          + "FROM ClickEventEntity c "
          + "WHERE c.linkId IN (SELECT l.id FROM LinkEntity l WHERE l.userId = :userId) "
          + "AND c.bot = false "
          + "AND c.clickedAt >= :from AND c.clickedAt < :to "
          + "GROUP BY dow, hour ORDER BY count DESC")
  List<HeatmapRow> findHeatmapByUserIdAndRange(
      @Param("userId") Long userId,
      @Param("from") Instant from,
      @Param("to") Instant to,
      @Param("tz") String tz,
      Pageable pageable);

  interface ReturnRateRow {
    Long getNewCount();

    Long getReturningCount();
  }

  interface DayClickRow {
    Integer getDay();

    Long getCount();
  }

  interface LinkClickCount {
    Long getLinkId();

    Long getCount();
  }

  interface DailyClickRow {
    LocalDate getDay();

    Long getCount();
  }

  interface DailyClicksByLinkRow {
    Long getLinkId();

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

  interface HeatmapRow {
    Integer getDow();

    Integer getHour();

    Long getCount();
  }

  interface ReferrerClickRow {
    String getReferrer();

    Long getCount();
  }

  interface ReferrerHostClickRow {
    String getHost();

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

  interface BotClickRow {
    String getBot();

    Long getCount();
  }

  interface UtmCampaignClickRow {
    String getCampaign();

    Long getCount();
  }

  interface UtmSourceClickRow {
    String getSource();

    Long getCount();
  }

  interface UtmMediumClickRow {
    String getMedium();

    Long getCount();
  }

  interface UtmContentClickRow {
    String getContent();

    Long getCount();
  }

  interface SourceChannelClickRow {
    String getSource();

    Long getCount();
  }

  interface DestinationClickRow {
    Long getDestinationId();

    Long getCount();
  }

  interface CountryClickRow {
    String getCountry();

    Long getCount();
  }

  interface RegionClickRow {
    String getRegion();

    Long getCount();
  }

  interface CityClickRow {
    String getCity();

    Long getCount();
  }

  interface LanguageClickRow {
    String getLanguage();

    Long getCount();
  }

  interface AsnClickRow {
    Integer getAsn();

    String getOrganization();

    Long getCount();
  }
}
