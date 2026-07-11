package com.example.short_link.post.collection.infrastructure;

import com.example.short_link.post.collection.domain.CollectionConnectionEntity;
import com.example.short_link.post.collection.domain.ConnectionBlockType;
import com.example.short_link.post.collection.domain.DiscoverConnectionRow;
import com.example.short_link.post.collection.domain.repository.CollectionConnectionRepository;
import com.example.short_link.post.collection.domain.repository.projection.CurationGraphProjections.CooccurrenceRow;
import com.example.short_link.post.collection.domain.repository.projection.CurationGraphProjections.CuratorOverlapRow;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
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
  public List<CollectionConnectionEntity> findAllByCollectionIdInOrderByPositionDesc(
      Collection<Long> collectionIds) {
    if (collectionIds.isEmpty()) return List.of();
    return jpa.findAllByCollectionIdInOrderByPositionDesc(collectionIds);
  }

  @Override
  public List<DiscoverConnectionRow> findPublicConnectionsByOwners(
      Collection<Long> ownerIds, int page, int size) {
    if (ownerIds.isEmpty()) return List.of();
    return jpa.findPublicConnectionsByOwners(ownerIds, PageRequest.of(page, size));
  }

  @Override
  public List<DiscoverConnectionRow> findRecentPublicConnections(int page, int size) {
    return jpa.findRecentPublicConnections(PageRequest.of(page, size));
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
  public List<CollectionConnectionEntity> findAllByBlockTypeAndRefId(
      ConnectionBlockType blockType, Long refId) {
    return jpa.findAllByBlockTypeAndRefId(blockType, refId);
  }

  @Override
  public Integer findMaxPositionByCollectionId(Long collectionId) {
    return jpa.findMaxPositionByCollectionId(collectionId);
  }

  @Override
  public List<CooccurrenceRow> findCooccurring(
      ConnectionBlockType blockType, Long refId, int limit) {
    return jpa.findCooccurring(blockType.name(), refId, limit);
  }

  @Override
  public List<CuratorOverlapRow> findOverlappingCurators(Long ownerId, int limit) {
    return jpa.findOverlappingCurators(ownerId, limit);
  }
}
