package com.example.short_link.post.collection.domain.repository;

import com.example.short_link.post.collection.domain.CollectionConnectionEntity;
import com.example.short_link.post.collection.domain.ConnectionBlockType;
import com.example.short_link.post.collection.domain.DiscoverConnectionRow;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CollectionConnectionRepository {

  /** 발견 피드 — 주어진 큐레이터들의 *공개* 컬렉션에 최근 이어진 연결(최신순 한 페이지). ownerIds 가 비면 빈 목록. */
  List<DiscoverConnectionRow> findPublicConnectionsByOwners(
      Collection<Long> ownerIds, int page, int size);

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

  /** Highest position in a collection (for append), or null when empty. */
  Integer findMaxPositionByCollectionId(Long collectionId);
}
