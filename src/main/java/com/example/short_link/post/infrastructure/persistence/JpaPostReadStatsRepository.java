package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.link.stats.domain.repository.projection.ClickProjections;
import com.example.short_link.post.domain.PostViewEventEntity;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Reader-breakdown queries over post_view_event, scoped to a set of post ids. Mirrors
 * JpaProfileVisitRepository (same CONVERT_TZ time bucketing, human-only filter, projection rows) so
 * the post/series reader dashboard matches the profile-visit dashboard. Used only via
 * PostReadStatsReaderAdapter.
 */
public interface JpaPostReadStatsRepository extends JpaRepository<PostViewEventEntity, Long> {

  @Query("SELECT COUNT(e) FROM PostViewEventEntity e WHERE e.postId IN :ids")
  long countViews(@Param("ids") Collection<Long> ids);

  @Query("SELECT COUNT(e) FROM PostViewEventEntity e WHERE e.postId IN :ids AND e.bot = false")
  long countHuman(@Param("ids") Collection<Long> ids);

  @Query("SELECT COUNT(e) FROM PostViewEventEntity e WHERE e.postId IN :ids AND e.bot = true")
  long countBot(@Param("ids") Collection<Long> ids);

  @Query(
      "SELECT COUNT(DISTINCT e.visitorHash) FROM PostViewEventEntity e "
          + "WHERE e.postId IN :ids AND e.bot = false AND e.visitorHash IS NOT NULL")
  long countUnique(@Param("ids") Collection<Long> ids);

  @Query(
      "SELECT MIN(e.viewedAt) FROM PostViewEventEntity e "
          + "WHERE e.postId IN :ids AND e.bot = false")
  Instant firstViewedAt(@Param("ids") Collection<Long> ids);

  @Query(
      "SELECT MAX(e.viewedAt) FROM PostViewEventEntity e "
          + "WHERE e.postId IN :ids AND e.bot = false")
  Instant lastViewedAt(@Param("ids") Collection<Long> ids);

  @Query(
      "SELECT FUNCTION('DATE', FUNCTION('CONVERT_TZ', e.viewedAt, '+00:00', :tz)) AS day, "
          + "COUNT(e) AS count "
          + "FROM PostViewEventEntity e WHERE e.postId IN :ids AND e.bot = false "
          + "AND e.viewedAt >= :from "
          + "GROUP BY FUNCTION('DATE', FUNCTION('CONVERT_TZ', e.viewedAt, '+00:00', :tz)) "
          + "ORDER BY day")
  List<ClickProjections.DailyClickRow> daily(
      @Param("ids") Collection<Long> ids, @Param("from") Instant from, @Param("tz") String tz);

  @Query(
      "SELECT FUNCTION('HOUR', FUNCTION('CONVERT_TZ', e.viewedAt, '+00:00', :tz)) AS hour, "
          + "COUNT(e) AS count "
          + "FROM PostViewEventEntity e WHERE e.postId IN :ids AND e.bot = false "
          + "GROUP BY FUNCTION('HOUR', FUNCTION('CONVERT_TZ', e.viewedAt, '+00:00', :tz)) "
          + "ORDER BY hour")
  List<ClickProjections.HourClickRow> hourly(
      @Param("ids") Collection<Long> ids, @Param("tz") String tz);

  @Query(
      "SELECT FUNCTION('DAYOFWEEK', FUNCTION('CONVERT_TZ', e.viewedAt, '+00:00', :tz)) AS dow, "
          + "FUNCTION('HOUR', FUNCTION('CONVERT_TZ', e.viewedAt, '+00:00', :tz)) AS hour, "
          + "COUNT(e) AS count "
          + "FROM PostViewEventEntity e WHERE e.postId IN :ids AND e.bot = false "
          + "GROUP BY dow, hour ORDER BY dow, hour")
  List<ClickProjections.HeatmapRow> heatmap(
      @Param("ids") Collection<Long> ids, @Param("tz") String tz);

  @Query(
      "SELECT e.countryCode AS country, COUNT(e) AS count "
          + "FROM PostViewEventEntity e WHERE e.postId IN :ids AND e.bot = false "
          + "GROUP BY e.countryCode ORDER BY count DESC")
  List<ClickProjections.CountryClickRow> topCountries(
      @Param("ids") Collection<Long> ids, Pageable pageable);

  @Query(
      "SELECT e.deviceClass AS device, COUNT(e) AS count "
          + "FROM PostViewEventEntity e WHERE e.postId IN :ids AND e.bot = false "
          + "GROUP BY e.deviceClass ORDER BY count DESC")
  List<ClickProjections.DeviceClickRow> topDevices(
      @Param("ids") Collection<Long> ids, Pageable pageable);

  @Query(
      "SELECT e.browserName AS browser, COUNT(e) AS count "
          + "FROM PostViewEventEntity e WHERE e.postId IN :ids AND e.bot = false "
          + "GROUP BY e.browserName ORDER BY count DESC")
  List<ClickProjections.BrowserClickRow> topBrowsers(
      @Param("ids") Collection<Long> ids, Pageable pageable);

  @Query(
      "SELECT e.referrerHost AS host, COUNT(e) AS count "
          + "FROM PostViewEventEntity e WHERE e.postId IN :ids AND e.bot = false "
          + "AND e.referrerHost IS NOT NULL "
          + "GROUP BY e.referrerHost ORDER BY count DESC")
  List<ClickProjections.ReferrerHostClickRow> topReferrerHosts(
      @Param("ids") Collection<Long> ids, Pageable pageable);

  @Query(
      "SELECT e.sourceChannel AS source, COUNT(e) AS count "
          + "FROM PostViewEventEntity e WHERE e.postId IN :ids AND e.bot = false "
          + "AND e.sourceChannel IS NOT NULL "
          + "GROUP BY e.sourceChannel ORDER BY count DESC")
  List<ClickProjections.SourceChannelClickRow> topSourceChannels(
      @Param("ids") Collection<Long> ids, Pageable pageable);

  @Query(
      "SELECT e.utmCampaign AS campaign, COUNT(e) AS count "
          + "FROM PostViewEventEntity e WHERE e.postId IN :ids AND e.bot = false "
          + "AND e.utmCampaign IS NOT NULL "
          + "GROUP BY e.utmCampaign ORDER BY count DESC")
  List<ClickProjections.UtmCampaignClickRow> topUtmCampaigns(
      @Param("ids") Collection<Long> ids, Pageable pageable);

  @Query(
      "SELECT e.utmSource AS source, COUNT(e) AS count "
          + "FROM PostViewEventEntity e WHERE e.postId IN :ids AND e.bot = false "
          + "AND e.utmSource IS NOT NULL "
          + "GROUP BY e.utmSource ORDER BY count DESC")
  List<ClickProjections.UtmSourceClickRow> topUtmSources(
      @Param("ids") Collection<Long> ids, Pageable pageable);
}
