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

  /** PUBLIC 컬렉션의 연결을 최신순으로 반환한다. {@code ownerIds}가 비면 빈 목록이다. */
  List<DiscoverConnectionRow> findPublicConnectionsByOwners(
      Collection<Long> ownerIds, int page, int size);

  /** 큐레이터 제한 없이 PUBLIC 컬렉션의 연결을 최신순으로 반환한다. */
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

  /** {@code refIds}가 비면 빈 목록을 반환한다. */
  List<CollectionConnectionEntity> findAllByBlockTypeAndRefIdIn(
      ConnectionBlockType blockType, Collection<Long> refIds);

  /** {@code collectionIds}가 비면 빈 목록을 반환한다. */
  List<CollectionConnectionCount> countByCollectionIdIn(Collection<Long> collectionIds);

  /**
   * (position, id) 정렬의 1-based 순위다. 삭제·재배치로 저장 위치가 듬성해져도 연속이다. {@code collectionIds}가 비면 빈 목록을
   * 반환한다.
   */
  List<CollectionConnectionRank> findRanksByCollectionIdsAndBlockType(
      Collection<Long> collectionIds, ConnectionBlockType blockType);

  /** 공동 등장 — 이 블록과 같은 공개 컬렉션에 함께 놓인 블록들(자기 제외), 함께 놓인 컬렉션 수 큰 순. */
  List<CooccurrenceRow> findCooccurring(ConnectionBlockType blockType, Long refId, int limit);

  /** 큐레이터 겹침 — 이 큐레이터의 공개 컬렉션 블록을 같이 엮은 다른 큐레이터들, 겹치는 블록 수 큰 순. */
  List<CuratorOverlapRow> findOverlappingCurators(Long ownerId, int limit);
}
