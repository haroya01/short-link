package com.example.short_link.tag.infrastructure.persistence;

import com.example.short_link.tag.domain.TagEntity;
import com.example.short_link.tag.domain.repository.TagRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class TagRepositoryAdapter implements TagRepository {

  private final JpaTagRepository jpa;

  @Override
  public Optional<TagEntity> findById(Long id) {
    return jpa.findById(id);
  }

  @Override
  public TagEntity save(TagEntity tag) {
    return jpa.save(tag);
  }

  @Override
  public void delete(TagEntity tag) {
    jpa.delete(tag);
  }

  @Override
  public List<TagEntity> findAllById(Collection<Long> ids) {
    return jpa.findAllById(ids);
  }

  @Override
  public List<TagEntity> findAllByUserIdOrderByNameAsc(Long userId) {
    return jpa.findAllByUserIdOrderByNameAsc(userId);
  }

  @Override
  public Optional<TagEntity> findFirstByUserIdAndName(Long userId, String name) {
    return jpa.findFirstByUserIdAndName(userId, name);
  }

  @Override
  public List<TagEntity> findAllByUserIdAndNameIn(Long userId, List<String> names) {
    return jpa.findAllByUserIdAndNameIn(userId, names);
  }

  @Override
  public List<Object[]> countLinksByTagIds(List<Long> tagIds) {
    return jpa.countLinksByTagIds(tagIds);
  }
}
