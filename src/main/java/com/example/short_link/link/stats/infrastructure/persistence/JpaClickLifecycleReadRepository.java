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
}
