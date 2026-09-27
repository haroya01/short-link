package com.example.short_link.post.collection.domain.repository;

import com.example.short_link.post.collection.domain.CollectionConnectionCount;
import com.example.short_link.post.collection.domain.CollectionConnectionEntity;
import com.example.short_link.post.collection.domain.CollectionConnectionRank;
import com.example.short_link.post.collection.domain.ConnectionBlockType;
import com.example.short_link.post.collection.domain.DiscoverConnectionRow;
import com.example.short_link.post.collection.domain.repository.projection.CurationGraphProjections.CooccurrenceRow;
import com.example.short_link.post.collection.domain.repository.projection.CurationGraphProjections.CuratorOverlapRow;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CollectionConnectionRepository {

  List<DiscoverConnectionRow> findPublicConnectionsByOwners(
      Collection<Long> ownerIds, int page, int size);

  List<DiscoverConnectionRow> findRecentPublicConnections(int page, int size);

  CollectionConnectionEntity save(CollectionConnectionEntity connection);

  Optional<CollectionConnectionEntity> findById(Long id);

  void delete(CollectionConnectionEntity connection);

  List<CollectionConnectionEntity> findAllByCollectionIdOrderByPositionAsc(Long collectionId);

  List<CollectionConnectionEntity> findAllByCollectionIdInOrderByPositionDesc(
      Collection<Long> collectionIds);

  long countByCollectionId(Long collectionId);

  List<CollectionConnectionEntity> findAllByBlockTypeAndRefId(
      ConnectionBlockType blockType, Long refId);

  List<CollectionConnectionEntity> findAllByBlockTypeAndRefIdIn(
      ConnectionBlockType blockType, Collection<Long> refIds);

  List<CollectionConnectionCount> countByCollectionIdIn(Collection<Long> collectionIds);

  List<CollectionConnectionRank> findRanksByCollectionIdsAndBlockType(
      Collection<Long> collectionIds, ConnectionBlockType blockType);

  List<CooccurrenceRow> findCooccurring(ConnectionBlockType blockType, Long refId, int limit);

  List<CuratorOverlapRow> findOverlappingCurators(Long ownerId, int limit);
}
