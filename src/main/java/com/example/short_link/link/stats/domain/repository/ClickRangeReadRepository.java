package com.example.short_link.link.stats.domain.repository;

import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.stats.domain.ClickEventEntity;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.HeatmapRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.LinkClickCount;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.UtmSourceClickRow;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Windowed (from..to) counts and aggregates — used by admin analytics for owner-scoped slices and
 * by webhook daily-summary for per-link snapshots. Distinct from {@link
 * ClickTotalsReadRepository}'s lifetime totals: every method here takes a closed time range.
 */
public interface ClickRangeReadRepository extends Repository<ClickEventEntity, Long> {

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
      @Param("linkId") LinkId linkId,
      @Param("from") Instant from,
      @Param("to") Instant to,
      Pageable pageable);

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

  @Query(
      "SELECT COUNT(c) FROM ClickEventEntity c "
          + "WHERE c.linkId = :linkId AND c.bot = false "
          + "AND c.clickedAt >= :from AND c.clickedAt < :to")
  long countHumanByLinkIdAndRange(
      @Param("linkId") LinkId linkId, @Param("from") Instant from, @Param("to") Instant to);

  @Query(
      "SELECT COUNT(c) FROM ClickEventEntity c "
          + "WHERE c.linkId = :linkId AND c.bot = true "
          + "AND c.clickedAt >= :from AND c.clickedAt < :to")
  long countBotByLinkIdAndRange(
      @Param("linkId") LinkId linkId, @Param("from") Instant from, @Param("to") Instant to);

  @Query(
      "SELECT COUNT(DISTINCT c.visitorHash) FROM ClickEventEntity c "
          + "WHERE c.linkId = :linkId AND c.bot = false AND c.visitorHash IS NOT NULL "
          + "AND c.clickedAt >= :from AND c.clickedAt < :to")
  long countUniqueVisitorsByLinkIdAndRange(
      @Param("linkId") LinkId linkId, @Param("from") Instant from, @Param("to") Instant to);
}
