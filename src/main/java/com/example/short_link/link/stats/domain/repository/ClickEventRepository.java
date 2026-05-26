package com.example.short_link.link.stats.domain.repository;

import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.stats.domain.ClickEventEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Click-event persistence + entity-returning CRUD reads. Analytics / projection queries are split
 * across narrower sibling interfaces in this package — {@code ClickTotalsReadRepository}, {@code
 * ClickTimeReadRepository}, {@code ClickDimensionReadRepository}, {@code
 * ClickLifecycleReadRepository}, {@code ClickRangeReadRepository}, {@code
 * ClickAlertReadRepository}. Each application service injects only the slices it needs so the
 * caller signature advertises its actual read surface.
 */
public interface ClickEventRepository extends JpaRepository<ClickEventEntity, Long> {

  long countByLinkId(LinkId linkId);

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
