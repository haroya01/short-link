package com.example.short_link.link.stats.infrastructure.persistence;

import com.example.short_link.link.stats.domain.ClickEventEntity;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.DayClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.ReturnRateRow;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface JpaClickLifecycleReadRepository extends Repository<ClickEventEntity, Long> {

  // 세션화한 재방문 — 같은 visitor 가 *30분 이상 간격을 두고 다시* 와야 returning(같은 자리 더블탭은 1회).
  // 이전엔 총 클릭수 ≥2 면 returning 이라, 연속 두 번 누르면 충성도가 부풀던 정직성 버그를 고친다.
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
              + "  WHERE link_id = :linkId AND visitor_hash IS NOT NULL AND is_bot = 0 "
              + ") s GROUP BY visitor_hash) t",
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

  /**
   * 채널(referrer host)을 시간축까지 포함해 한 번에 읽는다 — 클릭 수 · 첫 등장 시각 · 방문자 수 · 그중 재방문 수.
   *
   * <p>재방문 정의는 {@link #findReturnRate} 와 같은 30분 세션화다. "총 클릭 ≥2 = 재방문"으로 세면 같은 자리에서 두 번 누른 사람이 충성
   * 독자로 둔갑하는데, 링크 전체 재방문율은 이미 그 버그를 고쳤으므로 채널별 숫자만 옛 정의로 두면 두 수치가 서로를 부정한다.
   *
   * <p>윈도 함수가 링크의 사람 클릭 전체를 훑으므로 비용은 {@link #findReturnRate} 와 같은 급이다. 상위 N 채널로 잘라내는 건 바깥 LIMIT 뿐이라
   * 스캔량을 줄이지는 못한다 — 대신 응답 크기를 묶어 둔다.
   */
  @Query(
      value =
          "SELECT h.referrer_host AS host, h.clicks AS count, h.firstSeenEpoch AS firstSeenEpoch, "
              + "COALESCE(v.visitors, 0) AS visitors, "
              + "COALESCE(v.returningVisitors, 0) AS returningVisitors "
              + "FROM (SELECT referrer_host, COUNT(*) AS clicks, "
              + "        UNIX_TIMESTAMP(MIN(clicked_at)) AS firstSeenEpoch "
              + "      FROM click_event "
              + "      WHERE link_id = :linkId AND is_bot = 0 AND referrer_host IS NOT NULL "
              + "        AND referrer_host <> '' "
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
              + "                         AND referrer_host <> '' AND visitor_hash IS NOT NULL) s "
              + "                 GROUP BY referrer_host, visitor_hash) g "
              + "           GROUP BY referrer_host) v ON v.referrer_host = h.referrer_host "
              + "ORDER BY h.clicks DESC LIMIT :limit",
      nativeQuery = true)
  List<
          com.example.short_link.link.stats.domain.repository.projection.ClickProjections
              .ChannelDepthRow>
      findChannelDepth(@Param("linkId") Long linkId, @Param("limit") int limit);

  // referrer host 별 최초 등장 시각 — 가장 이른 host = 원래 채널, 한참 뒤 등장한 host = "넘어간" 채널.
  @Query(
      value =
          "SELECT referrer_host AS host, UNIX_TIMESTAMP(MIN(clicked_at)) AS firstSeenEpoch "
              + "FROM click_event "
              + "WHERE link_id = :linkId AND is_bot = 0 AND referrer_host IS NOT NULL "
              + "AND referrer_host <> '' "
              + "GROUP BY referrer_host ORDER BY firstSeenEpoch",
      nativeQuery = true)
  List<
          com.example.short_link.link.stats.domain.repository.projection.ClickProjections
              .HostFirstSeenRow>
      findFirstSeenByReferrerHost(@Param("linkId") Long linkId);
}
