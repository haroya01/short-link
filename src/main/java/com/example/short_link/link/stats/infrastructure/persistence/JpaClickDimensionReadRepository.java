package com.example.short_link.link.stats.infrastructure.persistence;

import com.example.short_link.link.stats.domain.ClickEventEntity;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.AsnClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.BotClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.BrowserClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.CityClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.CountryClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.DestinationClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.DeviceClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.LanguageClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.OsClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.ReferrerClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.ReferrerHostClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.RegionClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.SourceChannelClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.UtmCampaignClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.UtmContentClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.UtmMediumClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.UtmSourceClickRow;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface JpaClickDimensionReadRepository extends Repository<ClickEventEntity, Long> {

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
}
