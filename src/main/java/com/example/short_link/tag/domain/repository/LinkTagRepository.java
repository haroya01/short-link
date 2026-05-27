package com.example.short_link.tag.domain.repository;

import com.example.short_link.tag.domain.LinkTagEntity;
import java.util.Collection;
import java.util.List;

public interface LinkTagRepository {

  LinkTagEntity save(LinkTagEntity linkTag);

  List<LinkTagEntity> findAllByLinkId(Long linkId);

  List<LinkTagEntity> findAllByLinkIdIn(Collection<Long> linkIds);

  List<LinkTagEntity> findAllByTagId(Long tagId);

  int deleteByLinkId(Long linkId);

  int deleteByTagId(Long tagId);
}
