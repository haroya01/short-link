package com.example.short_link.tag.infrastructure.persistence;

import com.example.short_link.tag.domain.LinkTagEntity;
import com.example.short_link.tag.domain.repository.LinkTagRepository;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class LinkTagRepositoryAdapter implements LinkTagRepository {

  private final JpaLinkTagRepository jpa;

  @Override
  public LinkTagEntity save(LinkTagEntity linkTag) {
    return jpa.save(linkTag);
  }

  @Override
  public List<LinkTagEntity> findAllByLinkId(Long linkId) {
    return jpa.findAllByLinkId(linkId);
  }

  @Override
  public List<LinkTagEntity> findAllByLinkIdIn(Collection<Long> linkIds) {
    return jpa.findAllByLinkIdIn(linkIds);
  }

  @Override
  public List<LinkTagEntity> findAllByTagId(Long tagId) {
    return jpa.findAllByTagId(tagId);
  }

  @Override
  public int deleteByLinkId(Long linkId) {
    return jpa.deleteByLinkId(linkId);
  }

  @Override
  public int deleteByTagId(Long tagId) {
    return jpa.deleteByTagId(tagId);
  }
}
