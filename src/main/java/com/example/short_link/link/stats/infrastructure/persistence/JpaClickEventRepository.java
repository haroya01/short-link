package com.example.short_link.link.stats.infrastructure.persistence;

import com.example.short_link.link.stats.domain.ClickEventEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaClickEventRepository extends JpaRepository<ClickEventEntity, Long> {

  long countByLinkId(Long linkId);

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
