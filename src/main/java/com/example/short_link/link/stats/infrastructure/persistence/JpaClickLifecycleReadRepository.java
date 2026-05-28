package com.example.short_link.link.stats.infrastructure.persistence;

import com.example.short_link.link.stats.domain.ClickEventEntity;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.DayClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.ReturnRateRow;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface JpaClickLifecycleReadRepository extends Repository<ClickEventEntity, Long> {

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
}
