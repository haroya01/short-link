package com.example.short_link.link.stats.infrastructure.persistence;

import com.example.short_link.link.stats.domain.ClickEventEntity;
import com.example.short_link.link.stats.domain.ClickRollup;
import com.example.short_link.link.stats.domain.projection.ClickProjections.AsnClickRow;
import com.example.short_link.link.stats.domain.projection.ClickProjections.BotClickRow;
import com.example.short_link.link.stats.domain.projection.ClickProjections.BrowserClickRow;
import com.example.short_link.link.stats.domain.projection.ClickProjections.CityClickRow;
import com.example.short_link.link.stats.domain.projection.ClickProjections.ClientAppClickRow;
import com.example.short_link.link.stats.domain.projection.ClickProjections.CountryClickRow;
import com.example.short_link.link.stats.domain.projection.ClickProjections.DestinationClickRow;
import com.example.short_link.link.stats.domain.projection.ClickProjections.DeviceClickRow;
import com.example.short_link.link.stats.domain.projection.ClickProjections.FetchSiteClickRow;
import com.example.short_link.link.stats.domain.projection.ClickProjections.LanguageClickRow;
import com.example.short_link.link.stats.domain.projection.ClickProjections.OsClickRow;
import com.example.short_link.link.stats.domain.projection.ClickProjections.PostClickRow;
import com.example.short_link.link.stats.domain.projection.ClickProjections.ReferrerClickRow;
import com.example.short_link.link.stats.domain.projection.ClickProjections.ReferrerHostClickRow;
import com.example.short_link.link.stats.domain.projection.ClickProjections.RegionClickRow;
import com.example.short_link.link.stats.domain.projection.ClickProjections.SourceChannelClickRow;
import com.example.short_link.link.stats.domain.projection.ClickProjections.UtmCampaignClickRow;
import com.example.short_link.link.stats.domain.projection.ClickProjections.UtmContentClickRow;
import com.example.short_link.link.stats.domain.projection.ClickProjections.UtmMediumClickRow;
import com.example.short_link.link.stats.domain.projection.ClickProjections.UtmSourceClickRow;
import com.example.short_link.link.stats.domain.projection.ClickProjections.UtmTermClickRow;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface JpaClickDimensionReadRepository extends Repository<ClickEventEntity, Long> {

  String RAW = " AND clicked_at >= " + ClickRollup.ROLLED_UNTIL;

  @Query(
      value =
          "SELECT x AS referrer, CAST(SUM(n) AS SIGNED) AS count FROM ("
              + "SELECT referrer AS x, COUNT(*) AS n FROM click_event WHERE link_id = :linkId AND is_bot = 0 AND referrer IS NOT NULL"
              + RAW
              + " GROUP BY referrer UNION ALL SELECT value, SUM(clicks) FROM click_daily_dim "
              + "WHERE link_id = :linkId AND dim = "
              + ClickRollup.REFERRER
              + " AND is_bot = 0 AND value IS NOT NULL GROUP BY value) u GROUP BY x ORDER BY count DESC",
      nativeQuery = true)
  List<ReferrerClickRow> findReferrerClicks(@Param("linkId") Long linkId, Pageable pageable);

  @Query(
      value =
          "SELECT x AS host, CAST(SUM(n) AS SIGNED) AS count FROM ("
              + "SELECT referrer_host AS x, COUNT(*) AS n FROM click_event WHERE link_id = :linkId AND is_bot = 0 AND referrer_host IS NOT NULL"
              + RAW
              + " GROUP BY referrer_host UNION ALL SELECT value, SUM(clicks) FROM click_daily_dim "
              + "WHERE link_id = :linkId AND dim = "
              + ClickRollup.REFERRER_HOST
              + " AND is_bot = 0 AND value IS NOT NULL GROUP BY value) u GROUP BY x ORDER BY count DESC",
      nativeQuery = true)
  List<ReferrerHostClickRow> findReferrerHostClicks(
      @Param("linkId") Long linkId, Pageable pageable);

  @Query(
      value =
          "SELECT x AS device, CAST(SUM(n) AS SIGNED) AS count FROM ("
              + "SELECT device_class AS x, COUNT(*) AS n FROM click_event WHERE link_id = :linkId AND is_bot = 0"
              + RAW
              + " GROUP BY device_class UNION ALL SELECT value, SUM(clicks) FROM click_daily_dim "
              + "WHERE link_id = :linkId AND dim = "
              + ClickRollup.DEVICE
              + " AND is_bot = 0 GROUP BY value) u GROUP BY x ORDER BY count DESC",
      nativeQuery = true)
  List<DeviceClickRow> findDeviceClicks(@Param("linkId") Long linkId);

  @Query(
      value =
          "SELECT x AS os, CAST(SUM(n) AS SIGNED) AS count FROM ("
              + "SELECT os_name AS x, COUNT(*) AS n FROM click_event WHERE link_id = :linkId AND is_bot = 0"
              + RAW
              + " GROUP BY os_name UNION ALL SELECT value, SUM(clicks) FROM click_daily_dim "
              + "WHERE link_id = :linkId AND dim = "
              + ClickRollup.OS
              + " AND is_bot = 0 GROUP BY value) u GROUP BY x ORDER BY count DESC",
      nativeQuery = true)
  List<OsClickRow> findOsClicks(@Param("linkId") Long linkId, Pageable pageable);

  @Query(
      value =
          "SELECT x AS browser, CAST(SUM(n) AS SIGNED) AS count FROM ("
              + "SELECT browser_name AS x, COUNT(*) AS n FROM click_event WHERE link_id = :linkId AND is_bot = 0"
              + RAW
              + " GROUP BY browser_name UNION ALL SELECT value, SUM(clicks) FROM click_daily_dim "
              + "WHERE link_id = :linkId AND dim = "
              + ClickRollup.BROWSER
              + " AND is_bot = 0 GROUP BY value) u GROUP BY x ORDER BY count DESC",
      nativeQuery = true)
  List<BrowserClickRow> findBrowserClicks(@Param("linkId") Long linkId, Pageable pageable);

  @Query(
      value =
          "SELECT x AS bot, CAST(SUM(n) AS SIGNED) AS count FROM ("
              + "SELECT bot_name AS x, COUNT(*) AS n FROM click_event WHERE link_id = :linkId AND is_bot = 1"
              + RAW
              + " GROUP BY bot_name UNION ALL SELECT value, SUM(clicks) FROM click_daily_dim "
              + "WHERE link_id = :linkId AND dim = "
              + ClickRollup.BOT_NAME
              + " AND is_bot = 1 GROUP BY value) u GROUP BY x ORDER BY count DESC",
      nativeQuery = true)
  List<BotClickRow> findBotClicks(@Param("linkId") Long linkId, Pageable pageable);

  @Query(
      value =
          "SELECT x AS campaign, CAST(SUM(n) AS SIGNED) AS count FROM ("
              + "SELECT utm_campaign AS x, COUNT(*) AS n FROM click_event WHERE link_id = :linkId AND is_bot = 0 AND utm_campaign IS NOT NULL"
              + RAW
              + " GROUP BY utm_campaign UNION ALL SELECT value, SUM(clicks) FROM click_daily_dim "
              + "WHERE link_id = :linkId AND dim = "
              + ClickRollup.UTM_CAMPAIGN
              + " AND is_bot = 0 AND value IS NOT NULL GROUP BY value) u GROUP BY x ORDER BY count DESC",
      nativeQuery = true)
  List<UtmCampaignClickRow> findUtmCampaignClicks(@Param("linkId") Long linkId, Pageable pageable);

  @Query(
      value =
          "SELECT x AS source, CAST(SUM(n) AS SIGNED) AS count FROM ("
              + "SELECT utm_source AS x, COUNT(*) AS n FROM click_event WHERE link_id = :linkId AND is_bot = 0 AND utm_source IS NOT NULL"
              + RAW
              + " GROUP BY utm_source UNION ALL SELECT value, SUM(clicks) FROM click_daily_dim "
              + "WHERE link_id = :linkId AND dim = "
              + ClickRollup.UTM_SOURCE
              + " AND is_bot = 0 AND value IS NOT NULL GROUP BY value) u GROUP BY x ORDER BY count DESC",
      nativeQuery = true)
  List<UtmSourceClickRow> findUtmSourceClicks(@Param("linkId") Long linkId, Pageable pageable);

  @Query(
      value =
          "SELECT x AS medium, CAST(SUM(n) AS SIGNED) AS count FROM ("
              + "SELECT utm_medium AS x, COUNT(*) AS n FROM click_event WHERE link_id = :linkId AND is_bot = 0 AND utm_medium IS NOT NULL"
              + RAW
              + " GROUP BY utm_medium UNION ALL SELECT value, SUM(clicks) FROM click_daily_dim "
              + "WHERE link_id = :linkId AND dim = "
              + ClickRollup.UTM_MEDIUM
              + " AND is_bot = 0 AND value IS NOT NULL GROUP BY value) u GROUP BY x ORDER BY count DESC",
      nativeQuery = true)
  List<UtmMediumClickRow> findUtmMediumClicks(@Param("linkId") Long linkId, Pageable pageable);

  @Query(
      value =
          "SELECT x AS content, CAST(SUM(n) AS SIGNED) AS count FROM ("
              + "SELECT utm_content AS x, COUNT(*) AS n FROM click_event WHERE link_id = :linkId AND is_bot = 0 AND utm_content IS NOT NULL"
              + RAW
              + " GROUP BY utm_content UNION ALL SELECT value, SUM(clicks) FROM click_daily_dim "
              + "WHERE link_id = :linkId AND dim = "
              + ClickRollup.UTM_CONTENT
              + " AND is_bot = 0 AND value IS NOT NULL GROUP BY value) u GROUP BY x ORDER BY count DESC",
      nativeQuery = true)
  List<UtmContentClickRow> findUtmContentClicks(@Param("linkId") Long linkId, Pageable pageable);

  @Query(
      value =
          "SELECT x AS term, CAST(SUM(n) AS SIGNED) AS count FROM ("
              + "SELECT utm_term AS x, COUNT(*) AS n FROM click_event WHERE link_id = :linkId AND is_bot = 0 AND utm_term IS NOT NULL"
              + RAW
              + " GROUP BY utm_term UNION ALL SELECT value, SUM(clicks) FROM click_daily_dim "
              + "WHERE link_id = :linkId AND dim = "
              + ClickRollup.UTM_TERM
              + " AND is_bot = 0 AND value IS NOT NULL GROUP BY value) u GROUP BY x ORDER BY count DESC",
      nativeQuery = true)
  List<UtmTermClickRow> findUtmTermClicks(@Param("linkId") Long linkId, Pageable pageable);

  // 인앱 브라우저는 사람이다 — bot = false 로 사람 클릭만 세고, client_app IS NULL(일반 브라우저)은 뺀다.
  @Query(
      value =
          "SELECT x AS app, CAST(SUM(n) AS SIGNED) AS count FROM ("
              + "SELECT client_app AS x, COUNT(*) AS n FROM click_event WHERE link_id = :linkId AND is_bot = 0 AND client_app IS NOT NULL"
              + RAW
              + " GROUP BY client_app UNION ALL SELECT value, SUM(clicks) FROM click_daily_dim "
              + "WHERE link_id = :linkId AND dim = "
              + ClickRollup.CLIENT_APP
              + " AND is_bot = 0 AND value IS NOT NULL GROUP BY value) u GROUP BY x ORDER BY count DESC",
      nativeQuery = true)
  List<ClientAppClickRow> findClientAppClicks(@Param("linkId") Long linkId, Pageable pageable);

  @Query(
      value =
          "SELECT x AS fetchSite, CAST(SUM(n) AS SIGNED) AS count FROM ("
              + "SELECT fetch_site AS x, COUNT(*) AS n FROM click_event WHERE link_id = :linkId AND is_bot = 0 AND fetch_site IS NOT NULL"
              + RAW
              + " GROUP BY fetch_site UNION ALL SELECT value, SUM(clicks) FROM click_daily_dim "
              + "WHERE link_id = :linkId AND dim = "
              + ClickRollup.FETCH_SITE
              + " AND is_bot = 0 AND value IS NOT NULL GROUP BY value) u GROUP BY x ORDER BY count DESC",
      nativeQuery = true)
  List<FetchSiteClickRow> findFetchSiteClicks(@Param("linkId") Long linkId, Pageable pageable);

  @Query(
      value =
          "SELECT x AS postId, CAST(SUM(n) AS SIGNED) AS count FROM ("
              + "SELECT post_id AS x, COUNT(*) AS n FROM click_event WHERE link_id = :linkId AND is_bot = 0 AND post_id IS NOT NULL"
              + RAW
              + " GROUP BY post_id UNION ALL SELECT num, SUM(clicks) FROM click_daily_dim "
              + "WHERE link_id = :linkId AND dim = "
              + ClickRollup.POST
              + " AND is_bot = 0 AND num IS NOT NULL GROUP BY num) u GROUP BY x ORDER BY count DESC",
      nativeQuery = true)
  List<PostClickRow> findPostClicks(@Param("linkId") Long linkId, Pageable pageable);

  @Query(
      value =
          "SELECT x AS source, CAST(SUM(n) AS SIGNED) AS count FROM ("
              + "SELECT source_channel AS x, COUNT(*) AS n FROM click_event WHERE link_id = :linkId AND is_bot = 0 AND source_channel IS NOT NULL"
              + RAW
              + " GROUP BY source_channel UNION ALL SELECT value, SUM(clicks) FROM click_daily_dim "
              + "WHERE link_id = :linkId AND dim = "
              + ClickRollup.SOURCE_CHANNEL
              + " AND is_bot = 0 AND value IS NOT NULL GROUP BY value) u GROUP BY x ORDER BY count DESC",
      nativeQuery = true)
  List<SourceChannelClickRow> findSourceChannelClicks(
      @Param("linkId") Long linkId, Pageable pageable);

  @Query(
      value =
          "SELECT x AS destinationId, CAST(SUM(n) AS SIGNED) AS count FROM ("
              + "SELECT destination_id AS x, COUNT(*) AS n FROM click_event WHERE link_id = :linkId AND is_bot = 0"
              + RAW
              + " GROUP BY destination_id UNION ALL SELECT num, SUM(clicks) FROM click_daily_dim "
              + "WHERE link_id = :linkId AND dim = "
              + ClickRollup.DESTINATION
              + " AND is_bot = 0 GROUP BY num) u GROUP BY x ORDER BY count DESC",
      nativeQuery = true)
  List<DestinationClickRow> findDestinationClicks(@Param("linkId") Long linkId);

  @Query(
      value =
          "SELECT x AS country, CAST(SUM(n) AS SIGNED) AS count FROM ("
              + "SELECT country_code AS x, COUNT(*) AS n FROM click_event WHERE link_id = :linkId AND is_bot = 0"
              + RAW
              + " GROUP BY country_code UNION ALL SELECT value, SUM(clicks) FROM click_daily_dim "
              + "WHERE link_id = :linkId AND dim = "
              + ClickRollup.COUNTRY
              + " AND is_bot = 0 GROUP BY value) u GROUP BY x ORDER BY count DESC",
      nativeQuery = true)
  List<CountryClickRow> findCountryClicks(@Param("linkId") Long linkId, Pageable pageable);

  @Query(
      value =
          "SELECT x AS region, CAST(SUM(n) AS SIGNED) AS count FROM ("
              + "SELECT region_name AS x, COUNT(*) AS n FROM click_event WHERE link_id = :linkId AND is_bot = 0 AND region_name IS NOT NULL"
              + RAW
              + " GROUP BY region_name UNION ALL SELECT value, SUM(clicks) FROM click_daily_dim "
              + "WHERE link_id = :linkId AND dim = "
              + ClickRollup.REGION
              + " AND is_bot = 0 AND value IS NOT NULL GROUP BY value) u GROUP BY x ORDER BY count DESC",
      nativeQuery = true)
  List<RegionClickRow> findRegionClicks(@Param("linkId") Long linkId, Pageable pageable);

  @Query(
      value =
          "SELECT x AS city, CAST(SUM(n) AS SIGNED) AS count FROM ("
              + "SELECT city_name AS x, COUNT(*) AS n FROM click_event WHERE link_id = :linkId AND is_bot = 0 AND city_name IS NOT NULL"
              + RAW
              + " GROUP BY city_name UNION ALL SELECT value, SUM(clicks) FROM click_daily_dim "
              + "WHERE link_id = :linkId AND dim = "
              + ClickRollup.CITY
              + " AND is_bot = 0 AND value IS NOT NULL GROUP BY value) u GROUP BY x ORDER BY count DESC",
      nativeQuery = true)
  List<CityClickRow> findCityClicks(@Param("linkId") Long linkId, Pageable pageable);

  @Query(
      value =
          "SELECT x AS language, CAST(SUM(n) AS SIGNED) AS count FROM ("
              + "SELECT language AS x, COUNT(*) AS n FROM click_event WHERE link_id = :linkId AND is_bot = 0 AND language IS NOT NULL"
              + RAW
              + " GROUP BY language UNION ALL SELECT value, SUM(clicks) FROM click_daily_dim "
              + "WHERE link_id = :linkId AND dim = "
              + ClickRollup.LANGUAGE
              + " AND is_bot = 0 AND value IS NOT NULL GROUP BY value) u GROUP BY x ORDER BY count DESC",
      nativeQuery = true)
  List<LanguageClickRow> findLanguageClicks(@Param("linkId") Long linkId, Pageable pageable);

  @Query(
      value =
          "SELECT asn, organization, CAST(SUM(n) AS SIGNED) AS count FROM ("
              + "SELECT asn, asn_org AS organization, COUNT(*) AS n FROM click_event "
              + "WHERE link_id = :linkId AND asn_org IS NOT NULL"
              + RAW
              + " GROUP BY asn, asn_org UNION ALL SELECT num, value, SUM(clicks) "
              + "FROM click_daily_dim WHERE link_id = :linkId AND dim = "
              + ClickRollup.ASN
              + " GROUP BY num, value) u GROUP BY asn, organization ORDER BY count DESC",
      nativeQuery = true)
  List<AsnClickRow> findAsnClicks(@Param("linkId") Long linkId, Pageable pageable);
}
