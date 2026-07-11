package com.example.short_link.post.collection.domain.repository;

import com.example.short_link.post.collection.domain.CollectionConnectionCount;
import com.example.short_link.post.collection.domain.CollectionConnectionEntity;
import com.example.short_link.post.collection.domain.ConnectionBlockType;
import com.example.short_link.post.collection.domain.DiscoverConnectionRow;
import com.example.short_link.post.collection.domain.repository.projection.CurationGraphProjections.CooccurrenceRow;
import com.example.short_link.post.collection.domain.repository.projection.CurationGraphProjections.CuratorOverlapRow;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CollectionConnectionRepository {

  /** 발견 피드 — 주어진 큐레이터들의 *공개* 컬렉션에 최근 이어진 연결(최신순 한 페이지). ownerIds 가 비면 빈 목록. */
  List<DiscoverConnectionRow> findPublicConnectionsByOwners(
      Collection<Long> ownerIds, int page, int size);

  /** 공개 발견 피드 — 전역의 *공개* 컬렉션에 최근 이어진 연결(최신순 한 페이지). 큐레이터 제한 없음(비로그인 첫 표면용). */
  List<DiscoverConnectionRow> findRecentPublicConnections(int page, int size);

  CollectionConnectionEntity save(CollectionConnectionEntity connection);

  Optional<CollectionConnectionEntity> findById(Long id);

  void delete(CollectionConnectionEntity connection);

  /** A collection's connections in curator order. */
  List<CollectionConnectionEntity> findAllByCollectionIdOrderByPositionAsc(Long collectionId);

  /** Connections across several collections, most-recent first — drives list previews. */
  List<CollectionConnectionEntity> findAllByCollectionIdInOrderByPositionDesc(
      Collection<Long> collectionIds);

  long countByCollectionId(Long collectionId);

  boolean existsByCollectionIdAndBlockTypeAndRefId(
      Long collectionId, ConnectionBlockType blockType, Long refId);

  /** 이 블록(글·하이라이트·노트)을 담은 모든 연결 — "이 문장이 속한 길" 역조회. */
  List<CollectionConnectionEntity> findAllByBlockTypeAndRefId(
      ConnectionBlockType blockType, Long refId);

  /**
   * 여러 블록을 담은 모든 연결 — "이 문장이 속한 길"의 벌크판(피드가 보이는 카드 id 를 모아 한 번에). refIds 가 비면 빈 목록. 단건 역조회를 카드 수만큼
   * 반복하지 않게 in :refIds 한 쿼리로 묶는다.
   */
  List<CollectionConnectionEntity> findAllByBlockTypeAndRefIdIn(
      ConnectionBlockType blockType, Collection<Long> refIds);

  /** 컬렉션별 담긴 연결 수를 한 쿼리로 — 카드마다 count 를 세지 않게(N+1 방지). collectionIds 가 비면 빈 목록. */
  List<CollectionConnectionCount> countByCollectionIdIn(Collection<Long> collectionIds);

  /** Highest position in a collection (for append), or null when empty. */
  Integer findMaxPositionByCollectionId(Long collectionId);

  /** 공동 등장 — 이 블록과 같은 공개 컬렉션에 함께 놓인 블록들(자기 제외), 함께 놓인 컬렉션 수 큰 순. */
  List<CooccurrenceRow> findCooccurring(ConnectionBlockType blockType, Long refId, int limit);

  /** 큐레이터 겹침 — 이 큐레이터의 공개 컬렉션 블록을 같이 엮은 다른 큐레이터들, 겹치는 블록 수 큰 순. */
  List<CuratorOverlapRow> findOverlappingCurators(Long ownerId, int limit);
}
