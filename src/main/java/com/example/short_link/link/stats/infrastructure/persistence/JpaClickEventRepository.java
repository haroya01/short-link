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

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      value =
          "UPDATE click_event SET is_bot = 1, bot_name = :label WHERE id IN (:ids) "
              + "AND link_id = :linkId AND is_bot = 0 AND clicked_at >= FROM_UNIXTIME(:rolledUntil)",
      nativeQuery = true)
  int markHumansAs(
      @Param("linkId") Long linkId,
      @Param("ids") Collection<Long> ids,
      @Param("label") String label,
      @Param("rolledUntil") long rolledUntil);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      value =
          "UPDATE click_event SET is_bot = 0, bot_name = NULL WHERE id IN (:ids) "
              + "AND link_id = :linkId AND bot_name = :label "
              + "AND clicked_at >= FROM_UNIXTIME(:rolledUntil)",
      nativeQuery = true)
  int markAsHumans(
      @Param("linkId") Long linkId,
      @Param("ids") Collection<Long> ids,
      @Param("label") String label,
      @Param("rolledUntil") long rolledUntil);

  @Query(
      value =
          "SELECT DISTINCT FLOOR(UNIX_TIMESTAMP(clicked_at) / 86400) * 86400 FROM click_event "
              + "WHERE id IN (:ids) AND link_id = :linkId "
              + "AND clicked_at >= FROM_UNIXTIME(:from) AND clicked_at < FROM_UNIXTIME(:before)",
      nativeQuery = true)
  List<Number> findDayStartsOf(
      @Param("linkId") Long linkId,
      @Param("ids") Collection<Long> ids,
      @Param("from") long from,
      @Param("before") long before);
}
