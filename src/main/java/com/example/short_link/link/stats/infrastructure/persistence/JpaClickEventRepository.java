package com.example.short_link.link.stats.infrastructure.persistence;

import com.example.short_link.link.stats.domain.ClickEventEntity;
import com.example.short_link.link.stats.domain.ClickRollup;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaClickEventRepository extends JpaRepository<ClickEventEntity, Long> {

  @Query(
      value =
          "SELECT (SELECT COUNT(*) FROM click_event WHERE clicked_at >= "
              + ClickRollup.ROLLED_UNTIL
              + ") + (SELECT COALESCE(SUM(humans + bots), 0) FROM click_hourly)",
      nativeQuery = true)
  long countWithRollups();

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

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("DELETE FROM ClickEventEntity c WHERE c.linkId IN :linkIds")
  int deleteByLinkIds(@Param("linkIds") Collection<Long> linkIds);

  List<ClickEventEntity> findAllByLinkIdInOrderByClickedAtAsc(Collection<Long> linkIds);

  @Query(
      "SELECT c FROM ClickEventEntity c WHERE c.linkId = :linkId AND c.id < :cursorId "
          + "ORDER BY c.id DESC")
  List<ClickEventEntity> findEventsByLinkIdBefore(
      @Param("linkId") Long linkId, @Param("cursorId") Long cursorId, Pageable pageable);

  @Query("SELECT c FROM ClickEventEntity c WHERE c.linkId = :linkId ORDER BY c.id DESC")
  List<ClickEventEntity> findEventsByLinkIdLatest(@Param("linkId") Long linkId, Pageable pageable);
}
