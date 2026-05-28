package com.example.short_link.tag.domain.repository;

import com.example.short_link.tag.domain.TagEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TagRepository {

  Optional<TagEntity> findById(Long id);

  TagEntity save(TagEntity tag);

  void delete(TagEntity tag);

  List<TagEntity> findAllById(Collection<Long> ids);

  List<TagEntity> findAllByUserIdOrderByNameAsc(Long userId);

  Optional<TagEntity> findFirstByUserIdAndName(Long userId, String name);

  List<TagEntity> findAllByUserIdAndNameIn(Long userId, List<String> names);

  List<Object[]> countLinksByTagIds(List<Long> tagIds);
}
