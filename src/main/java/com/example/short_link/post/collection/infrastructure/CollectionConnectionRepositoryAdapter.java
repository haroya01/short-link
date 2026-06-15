package com.example.short_link.post.collection.infrastructure;

import com.example.short_link.post.collection.domain.CollectionConnectionEntity;
import com.example.short_link.post.collection.domain.ConnectionBlockType;
import com.example.short_link.post.collection.domain.repository.CollectionConnectionRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class CollectionConnectionRepositoryAdapter implements CollectionConnectionRepository {

  private final JpaCollectionConnectionRepository jpa;

  @Override
  public CollectionConnectionEntity save(CollectionConnectionEntity connection) {
    return jpa.save(connection);
  }

  @Override
  public Optional<CollectionConnectionEntity> findById(Long id) {
    return jpa.findById(id);
  }

  @Override
  public void delete(CollectionConnectionEntity connection) {
    jpa.delete(connection);
  }

  @Override
  public List<CollectionConnectionEntity> findAllByCollectionIdOrderByPositionAsc(
      Long collectionId) {
    return jpa.findAllByCollectionIdOrderByPositionAsc(collectionId);
  }

  @Override
  public long countByCollectionId(Long collectionId) {
    return jpa.countByCollectionId(collectionId);
  }

  @Override
  public boolean existsByCollectionIdAndBlockTypeAndRefId(
      Long collectionId, ConnectionBlockType blockType, Long refId) {
    return jpa.existsByCollectionIdAndBlockTypeAndRefId(collectionId, blockType, refId);
  }

  @Override
  public Integer findMaxPositionByCollectionId(Long collectionId) {
    return jpa.findMaxPositionByCollectionId(collectionId);
  }
}
