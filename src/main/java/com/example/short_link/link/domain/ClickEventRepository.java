package com.example.short_link.link.domain;

import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Click-event persistence + entity-returning reads. Analytics / projection queries live in {@link
 * ClickEventReadRepository} — splitting the surface so the write side stays a CRUD-shaped JPA
 * repository while the read side advertises its aggregate nature.
 *
 * <p>Inherits from {@link ClickEventReadRepository} so existing callers that injected {@code
 * ClickEventRepository} keep compiling — new callers should depend on the narrower {@link
 * ClickEventReadRepository} when they only need the projections, which is most of them. Migrating
 * existing callers to the narrower interface is a follow-up cleanup; this PR just splits the
 * surface so the next reader can tell which side they're using.
 */
public interface ClickEventRepository
    extends JpaRepository<ClickEventEntity, Long>, ClickEventReadRepository {

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
