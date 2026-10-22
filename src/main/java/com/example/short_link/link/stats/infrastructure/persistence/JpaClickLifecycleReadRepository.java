package com.example.short_link.link.stats.infrastructure.persistence;

import com.example.short_link.link.stats.domain.ClickEventEntity;
import com.example.short_link.link.stats.domain.ClickRollup;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.DayClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.ReturnRateRow;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface JpaClickLifecycleReadRepository extends Repository<ClickEventEntity, Long> {

  String RAW = " AND clicked_at >= " + ClickRollup.ROLLED_UNTIL;

  // 세션화한 재방문 — 같은 visitor 가 *30분 이상 간격을 두고 다시* 와야 returning(같은 자리 더블탭은 1회).
  // 방문자를 잇는 visitor_hash는 원본에만 있어 원본 보관 기간 안의 클릭만 센다.
  @Query(
      value =
          "SELECT SUM(CASE WHEN sessions = 1 THEN 1 ELSE 0 END) AS newCount, "
              + "SUM(CASE WHEN sessions >= 2 THEN 1 ELSE 0 END) AS returningCount "
              + "FROM (SELECT visitor_hash, SUM(is_new_session) AS sessions FROM ("
              + "  SELECT visitor_hash, "
              + "    CASE WHEN LAG(clicked_at) OVER (PARTITION BY visitor_hash ORDER BY clicked_at) IS NULL "
              + "         OR TIMESTAMPDIFF(MINUTE, "
              + "              LAG(clicked_at) OVER (PARTITION BY visitor_hash ORDER BY clicked_at), clicked_at) >= 30 "
              + "    THEN 1 ELSE 0 END AS is_new_session "
              + "  FROM click_event "
              + "  WHERE link_id = :linkId AND visitor_hash IS NOT NULL AND is_bot = 0"
              + RAW
              + ") s GROUP BY visitor_hash) t",
      nativeQuery = true)
  ReturnRateRow findReturnRate(@Param("linkId") Long linkId);

  @Query(
      value =
          "SELECT day, CAST(SUM(n) AS SIGNED) AS count FROM ("
              + "SELECT TIMESTAMPDIFF(DAY, l.created_at, c.clicked_at) AS day, COUNT(*) AS n "
              + "FROM click_event c JOIN link l ON c.link_id = l.id "
              + "WHERE l.id = :linkId AND c.is_bot = 0 AND c.clicked_at >= "
              + ClickRollup.ROLLED_UNTIL
              + " AND TIMESTAMPDIFF(DAY, l.created_at, c.clicked_at) BETWEEN 0 AND :maxDay "
              + "GROUP BY day UNION ALL "
              + "SELECT age_day, SUM(humans) FROM click_hourly WHERE link_id = :linkId "
              + "AND humans > 0 AND age_day BETWEEN 0 AND :maxDay GROUP BY age_day"
              + ") u GROUP BY day ORDER BY day",
      nativeQuery = true)
  List<DayClickRow> findLifecycleClicks(@Param("linkId") Long linkId, @Param("maxDay") int maxDay);

  // 재방문은 findReturnRate와 같은 30분 세션화 정의를 사용한다. 윈도 함수는 링크의 사람 클릭 전체를 훑으므로 바깥 LIMIT은 응답 크기만 줄이고 스캔량은
  // 줄이지 못한다. 클릭 수와 첫 등장은 집계를 더한 전체 기간, 방문자와 재방문은 원본 보관 기간 안이다.
  @Query(
      value =
          "SELECT h.referrer_host AS host, h.clicks AS count, h.firstSeenEpoch AS firstSeenEpoch, "
              + "COALESCE(v.visitors, 0) AS visitors, "
              + "COALESCE(v.returningVisitors, 0) AS returningVisitors "
              + "FROM (SELECT referrer_host, CAST(SUM(n) AS SIGNED) AS clicks, "
              + "        UNIX_TIMESTAMP(MIN(first_at)) AS firstSeenEpoch "
              + "      FROM (SELECT referrer_host, COUNT(*) AS n, MIN(clicked_at) AS first_at "
              + "            FROM click_event "
              + "            WHERE link_id = :linkId AND is_bot = 0 AND referrer_host IS NOT NULL "
              + "              AND referrer_host <> ''"
              + RAW
              + "            GROUP BY referrer_host UNION ALL "
              + "            SELECT value, SUM(clicks), MIN(first_at) FROM click_daily_dim "
              + "            WHERE link_id = :linkId AND dim = "
              + ClickRollup.REFERRER_HOST
              + "              AND is_bot = 0 AND value <> '' GROUP BY value) r "
              + "      GROUP BY referrer_host) h "
              + "LEFT JOIN (SELECT referrer_host, COUNT(*) AS visitors, "
              + "             SUM(CASE WHEN sessions >= 2 THEN 1 ELSE 0 END) AS returningVisitors "
              + "           FROM (SELECT referrer_host, visitor_hash, SUM(is_new_session) AS sessions "
              + "                 FROM (SELECT referrer_host, visitor_hash, "
              + "                         CASE WHEN LAG(clicked_at) OVER (PARTITION BY referrer_host, visitor_hash ORDER BY clicked_at) IS NULL "
              + "                              OR TIMESTAMPDIFF(MINUTE, "
              + "                                   LAG(clicked_at) OVER (PARTITION BY referrer_host, visitor_hash ORDER BY clicked_at), clicked_at) >= 30 "
              + "                         THEN 1 ELSE 0 END AS is_new_session "
              + "                       FROM click_event "
              + "                       WHERE link_id = :linkId AND is_bot = 0 AND referrer_host IS NOT NULL "
              + "                         AND referrer_host <> '' AND visitor_hash IS NOT NULL"
              + RAW
              + ") s "
              + "                 GROUP BY referrer_host, visitor_hash) g "
              + "           GROUP BY referrer_host) v ON v.referrer_host = h.referrer_host "
              + "ORDER BY h.clicks DESC LIMIT :limit",
      nativeQuery = true)
  List<
          com.example.short_link.link.stats.domain.repository.projection.ClickProjections
              .ChannelDepthRow>
      findChannelDepth(@Param("linkId") Long linkId, @Param("limit") int limit);

  @Query(
      value =
          "SELECT referrer_host AS host, UNIX_TIMESTAMP(MIN(first_at)) AS firstSeenEpoch "
              + "FROM (SELECT referrer_host, MIN(clicked_at) AS first_at FROM click_event "
              + "      WHERE link_id = :linkId AND is_bot = 0 AND referrer_host IS NOT NULL "
              + "        AND referrer_host <> ''"
              + RAW
              + "      GROUP BY referrer_host UNION ALL "
              + "      SELECT value, MIN(first_at) FROM click_daily_dim WHERE link_id = :linkId "
              + "        AND dim = "
              + ClickRollup.REFERRER_HOST
              + " AND is_bot = 0 AND value <> '' GROUP BY value) r "
              + "GROUP BY referrer_host ORDER BY firstSeenEpoch",
      nativeQuery = true)
  List<
          com.example.short_link.link.stats.domain.repository.projection.ClickProjections
              .HostFirstSeenRow>
      findFirstSeenByReferrerHost(@Param("linkId") Long linkId);
}
