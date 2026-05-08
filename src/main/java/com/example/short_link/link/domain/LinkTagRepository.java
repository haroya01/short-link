package com.example.short_link.link.domain;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LinkTagRepository extends JpaRepository<LinkTagEntity, LinkTagEntity.LinkTagId> {

  List<LinkTagEntity> findAllByLinkId(Long linkId);

  List<LinkTagEntity> findAllByLinkIdIn(Collection<Long> linkIds);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("DELETE FROM LinkTagEntity lt WHERE lt.linkId = :linkId")
  int deleteByLinkId(@Param("linkId") Long linkId);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("DELETE FROM LinkTagEntity lt WHERE lt.tagId = :tagId")
  int deleteByTagId(@Param("tagId") Long tagId);
}
