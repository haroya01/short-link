package com.example.short_link.tag.infrastructure.persistence;

import com.example.short_link.tag.domain.LinkTagEntity;
import com.example.short_link.tag.domain.repository.LinkTagRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaLinkTagRepository
    extends JpaRepository<LinkTagEntity, LinkTagEntity.LinkTagId>, LinkTagRepository {

  @Override
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("DELETE FROM LinkTagEntity lt WHERE lt.linkId = :linkId")
  int deleteByLinkId(@Param("linkId") Long linkId);

  @Override
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("DELETE FROM LinkTagEntity lt WHERE lt.tagId = :tagId")
  int deleteByTagId(@Param("tagId") Long tagId);
}
