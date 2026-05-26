package com.example.short_link.link.stats.domain.repository;

import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.stats.domain.ClickEventEntity;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.CountryClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.DeviceClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.HourClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.ReferrerHostClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.SourceChannelClickRow;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Top-1-of-dimension queries used by the daily-summary and threshold-spike webhook payloads. Native
 * SQL with {@code LIMIT 1} keeps Spring Data {@code Pageable} out of the application layer (rule
 * {@code springDataNotInApplication}); returning {@link Optional} so the caller stays branch-free.
 */
public interface ClickAlertReadRepository extends Repository<ClickEventEntity, Long> {

  @Query(
      value =
          "SELECT source_channel AS source, COUNT(*) AS count FROM click_event "
              + "WHERE link_id = :linkId AND is_bot = 0 AND source_channel IS NOT NULL "
              + "AND clicked_at >= :from AND clicked_at < :to "
              + "GROUP BY source_channel ORDER BY count DESC LIMIT 1",
      nativeQuery = true)
  Optional<SourceChannelClickRow> findTopChannelByLinkIdAndRange(
      @Param("linkId") LinkId linkId, @Param("from") Instant from, @Param("to") Instant to);

  @Query(
      value =
          "SELECT country_code AS country, COUNT(*) AS count FROM click_event "
              + "WHERE link_id = :linkId AND is_bot = 0 AND country_code IS NOT NULL "
              + "AND clicked_at >= :from AND clicked_at < :to "
              + "GROUP BY country_code ORDER BY count DESC LIMIT 1",
      nativeQuery = true)
  Optional<CountryClickRow> findTopCountryByLinkIdAndRange(
      @Param("linkId") LinkId linkId, @Param("from") Instant from, @Param("to") Instant to);

  @Query(
      value =
          "SELECT device_class AS device, COUNT(*) AS count FROM click_event "
              + "WHERE link_id = :linkId AND is_bot = 0 "
              + "AND clicked_at >= :from AND clicked_at < :to "
              + "GROUP BY device_class ORDER BY count DESC LIMIT 1",
      nativeQuery = true)
  Optional<DeviceClickRow> findTopDeviceByLinkIdAndRange(
      @Param("linkId") LinkId linkId, @Param("from") Instant from, @Param("to") Instant to);

  @Query(
      value =
          "SELECT HOUR(CONVERT_TZ(clicked_at, '+00:00', :tz)) AS hour, COUNT(*) AS count "
              + "FROM click_event "
              + "WHERE link_id = :linkId AND is_bot = 0 "
              + "AND clicked_at >= :from AND clicked_at < :to "
              + "GROUP BY HOUR(CONVERT_TZ(clicked_at, '+00:00', :tz)) "
              + "ORDER BY count DESC LIMIT 1",
      nativeQuery = true)
  Optional<HourClickRow> findPeakHourByLinkIdAndRange(
      @Param("linkId") LinkId linkId,
      @Param("from") Instant from,
      @Param("to") Instant to,
      @Param("tz") String tz);

  /**
   * Top referrer host for the link since {@code since}, used by the threshold-spike alert payload.
   */
  @Query(
      value =
          "SELECT referrer_host AS host, COUNT(*) AS count FROM click_event "
              + "WHERE link_id = :linkId AND is_bot = 0 AND referrer_host IS NOT NULL "
              + "AND clicked_at >= :since "
              + "GROUP BY referrer_host ORDER BY count DESC LIMIT 1",
      nativeQuery = true)
  Optional<ReferrerHostClickRow> findTopReferrerHostByLinkIdSince(
      @Param("linkId") LinkId linkId, @Param("since") Instant since);
}
