package com.example.short_link.link.stats.infrastructure.persistence;

import com.example.short_link.link.stats.domain.ClickEventEntity;
import com.example.short_link.link.stats.domain.ClickRollup;
import com.example.short_link.link.stats.domain.projection.ClickProjections.LinkClickCount;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface JpaClickTotalsReadRepository extends Repository<ClickEventEntity, Long> {

  String RAW = " AND clicked_at >= " + ClickRollup.ROLLED_UNTIL;

  @Query(
      value =
          "SELECT (SELECT COUNT(*) FROM click_event WHERE link_id = :linkId"
              + RAW
              + ") + (SELECT COALESCE(SUM(humans + bots), 0) FROM click_hourly "
              + "WHERE link_id = :linkId)",
      nativeQuery = true)
  long countByLinkId(@Param("linkId") Long linkId);

  @Query(
      value =
          "SELECT (SELECT COUNT(*) FROM click_event WHERE link_id = :linkId AND is_bot = 0"
              + RAW
              + ") + (SELECT COALESCE(SUM(humans), 0) FROM click_hourly WHERE link_id = :linkId)",
      nativeQuery = true)
  long countHumanByLinkId(@Param("linkId") Long linkId);

  @Query(
      value =
          "SELECT (SELECT COUNT(*) FROM click_event WHERE link_id = :linkId AND is_bot = 1"
              + RAW
              + ") + (SELECT COALESCE(SUM(bots), 0) FROM click_hourly WHERE link_id = :linkId)",
      nativeQuery = true)
  long countBotByLinkId(@Param("linkId") Long linkId);

  @Query(
      value =
          "SELECT (SELECT COUNT(*) FROM click_event WHERE link_id = :linkId AND is_bot = 0 "
              + "AND referrer IS NULL"
              + RAW
              + ") + (SELECT COALESCE(SUM(clicks), 0) FROM click_daily_dim "
              + "WHERE link_id = :linkId AND dim = "
              + ClickRollup.REFERRER
              + " AND is_bot = 0 AND value IS NULL)",
      nativeQuery = true)
  long countDirectByLinkId(@Param("linkId") Long linkId);

  @Query(
      value =
          "SELECT (SELECT COUNT(*) FROM click_event WHERE link_id = :linkId AND is_bot = 0 "
              + "AND source_channel LIKE 'profile-%'"
              + RAW
              + ") + (SELECT COALESCE(SUM(clicks), 0) FROM click_daily_dim "
              + "WHERE link_id = :linkId AND dim = "
              + ClickRollup.SOURCE_CHANNEL
              + " AND is_bot = 0 AND value LIKE 'profile-%')",
      nativeQuery = true)
  long countProfileChannelByLinkId(@Param("linkId") Long linkId);

  @Query(
      value =
          "SELECT COUNT(DISTINCT visitor_hash) AS recent, "
              + "EXISTS (SELECT 1 FROM click_daily_visitors v WHERE v.link_id = :linkId) "
              + "AS rolledUp, UNIX_TIMESTAMP("
              + ClickRollup.ROLLED_UNTIL
              + ") AS rolledUntil "
              + "FROM click_event WHERE link_id = :linkId AND is_bot = 0 "
              + "AND visitor_hash IS NOT NULL"
              + RAW,
      nativeQuery = true)
  RecentVisitorsRow countRecentVisitors(@Param("linkId") Long linkId);

  @Query(
      value = "SELECT sketch FROM click_daily_visitors WHERE link_id = :linkId",
      nativeQuery = true)
  List<byte[]> findVisitorSketches(@Param("linkId") Long linkId);

  @Query(
      value =
          "SELECT DISTINCT visitor_hash FROM click_event WHERE link_id = :linkId AND is_bot = 0 "
              + "AND visitor_hash IS NOT NULL"
              + RAW,
      nativeQuery = true)
  List<String> findRecentVisitorHashes(@Param("linkId") Long linkId);

  @Query(
      value =
          "SELECT UNIX_TIMESTAMP(COALESCE("
              + "(SELECT MIN(first_human_at) FROM click_hourly WHERE link_id = :linkId), "
              + "(SELECT MIN(clicked_at) FROM click_event WHERE link_id = :linkId AND is_bot = 0"
              + RAW
              + ")))",
      nativeQuery = true)
  Long findFirstClickEpoch(@Param("linkId") Long linkId);

  @Query(
      value =
          "SELECT UNIX_TIMESTAMP(COALESCE("
              + "(SELECT MAX(clicked_at) FROM click_event WHERE link_id = :linkId AND is_bot = 0"
              + RAW
              + "), (SELECT MAX(last_human_at) FROM click_hourly WHERE link_id = :linkId)))",
      nativeQuery = true)
  Long findLastClickEpoch(@Param("linkId") Long linkId);

  @Query(
      value =
          "SELECT (SELECT COUNT(*) FROM click_event WHERE link_id = :linkId AND is_bot = 1 "
              + "AND bot_name LIKE 'preview:%'"
              + RAW
              + ") + (SELECT COALESCE(SUM(clicks), 0) FROM click_daily_dim "
              + "WHERE link_id = :linkId AND dim = "
              + ClickRollup.BOT_NAME
              + " AND is_bot = 1 AND value LIKE 'preview:%')",
      nativeQuery = true)
  long countPreviewByLinkId(@Param("linkId") Long linkId);

  @Query(
      "SELECT COUNT(c) FROM ClickEventEntity c "
          + "WHERE c.linkId = :linkId AND c.bot = false AND c.clickedAt >= :since")
  long countSinceByLinkId(@Param("linkId") Long linkId, @Param("since") Instant since);

  @Query(
      value =
          "SELECT (SELECT COUNT(*) FROM click_event WHERE link_id = :linkId "
              + "AND bot_name LIKE 'datacenter:%'"
              + RAW
              + ") + (SELECT COALESCE(SUM(clicks), 0) FROM click_daily_dim "
              + "WHERE link_id = :linkId AND dim = "
              + ClickRollup.BOT_NAME
              + " AND value LIKE 'datacenter:%')",
      nativeQuery = true)
  long countDatacenterClicks(@Param("linkId") Long linkId);

  @Query(
      value =
          "SELECT link_id AS linkId, CAST(SUM(n) AS SIGNED) AS count FROM ("
              + "SELECT link_id, COUNT(*) AS n FROM click_event WHERE link_id IN (:ids)"
              + RAW
              + " GROUP BY link_id UNION ALL "
              + "SELECT link_id, SUM(humans + bots) FROM click_hourly WHERE link_id IN (:ids) "
              + "GROUP BY link_id) u GROUP BY link_id",
      nativeQuery = true)
  List<LinkClickCount> countsByLinkIds(@Param("ids") List<Long> ids);

  @Query(
      value =
          "SELECT link_id AS linkId, CAST(SUM(n) AS SIGNED) AS count FROM ("
              + "SELECT link_id, COUNT(*) AS n FROM click_event WHERE link_id IN (:ids) "
              + "AND is_bot = 0"
              + RAW
              + " GROUP BY link_id UNION ALL "
              + "SELECT link_id, SUM(humans) FROM click_hourly WHERE link_id IN (:ids) "
              + "GROUP BY link_id) u GROUP BY link_id HAVING SUM(n) > 0",
      nativeQuery = true)
  List<LinkClickCount> humanCountsByLinkIds(@Param("ids") List<Long> ids);

  // An hour that straddles the instant counts on the side holding its last human click.
  @Query(
      value =
          "SELECT link_id AS linkId, CAST(SUM(n) AS SIGNED) AS count FROM ("
              + "SELECT link_id, COUNT(*) AS n FROM click_event WHERE link_id IN (:ids) "
              + "AND is_bot = 0 AND clicked_at >= FROM_UNIXTIME(:since)"
              + RAW
              + " GROUP BY link_id UNION ALL "
              + "SELECT link_id, SUM(humans) FROM click_hourly WHERE link_id IN (:ids) "
              + "AND last_human_at >= FROM_UNIXTIME(:since) GROUP BY link_id) u GROUP BY link_id",
      nativeQuery = true)
  List<LinkClickCount> countsByLinkIdsSince(
      @Param("ids") List<Long> ids, @Param("since") BigDecimal since);

  @Query(
      value =
          "SELECT link_id AS linkId, CAST(SUM(n) AS SIGNED) AS count FROM ("
              + "SELECT link_id, COUNT(*) AS n FROM click_event WHERE link_id IN (:ids) "
              + "AND is_bot = 0 AND clicked_at < FROM_UNIXTIME(:before)"
              + RAW
              + " GROUP BY link_id UNION ALL "
              + "SELECT link_id, SUM(humans) FROM click_hourly WHERE link_id IN (:ids) "
              + "AND last_human_at < FROM_UNIXTIME(:before) GROUP BY link_id) u GROUP BY link_id",
      nativeQuery = true)
  List<LinkClickCount> countsByLinkIdsBefore(
      @Param("ids") List<Long> ids, @Param("before") BigDecimal before);

  @Query(
      value =
          "SELECT UNIX_TIMESTAMP(COALESCE("
              + "(SELECT MAX(clicked_at) FROM click_event WHERE link_id IN (:ids) AND is_bot = 0 "
              + "AND clicked_at < FROM_UNIXTIME(:before)"
              + RAW
              + "), (SELECT MAX(CASE WHEN last_human_at < FROM_UNIXTIME(:before) THEN last_human_at "
              + "ELSE first_human_at END) FROM click_hourly WHERE link_id IN (:ids) "
              + "AND first_human_at < FROM_UNIXTIME(:before))))",
      nativeQuery = true)
  Long findLastClickBeforeEpoch(@Param("ids") List<Long> ids, @Param("before") BigDecimal before);

  interface RecentVisitorsRow {
    Long getRecent();

    Long getRolledUp();

    Long getRolledUntil();
  }

  @Query(
      value =
          "SELECT link_id, app, CAST(SUM(n) AS SIGNED) FROM ("
              + "SELECT link_id, client_app AS app, COUNT(*) AS n FROM click_event "
              + "WHERE link_id IN (:ids) AND is_bot = 0 AND clicked_at >= "
              + ClickRollup.ROLLED_UNTIL
              + " GROUP BY link_id, client_app UNION ALL "
              + "SELECT link_id, value, SUM(clicks) FROM click_daily_dim WHERE link_id IN (:ids) "
              + "AND dim = "
              + ClickRollup.CLIENT_APP
              + " AND is_bot = 0 GROUP BY link_id, value) u GROUP BY link_id, app",
      nativeQuery = true)
  List<Object[]> humanClientAppCountsByLinkIds(@Param("ids") Collection<Long> ids);
}
