package com.example.short_link.link.stats.domain.repository;

import com.example.short_link.link.stats.domain.ClickEventEntity;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Write-side CRUD for click events. JpaRepository gives save/delete/findById and friends; the only
 * non-standard write here is {@link #deleteByLinkIds}. Inherits {@link ClickEventReadRepository} so
 * callers that need both write and entity reads still resolve everything off this interface.
 *
 * <p>Read consumers (analytics, projections, entity-only lookups by linkId) should depend on the
 * narrower {@link ClickEventReadRepository} directly.
 */
public interface ClickEventRepository
    extends JpaRepository<ClickEventEntity, Long>, ClickEventReadRepository {

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("DELETE FROM ClickEventEntity c WHERE c.linkId IN :linkIds")
  int deleteByLinkIds(@Param("linkIds") Collection<Long> linkIds);
}
