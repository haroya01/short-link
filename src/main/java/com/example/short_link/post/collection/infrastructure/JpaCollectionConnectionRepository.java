package com.example.short_link.post.collection.infrastructure;

import com.example.short_link.post.collection.domain.CollectionConnectionEntity;
import com.example.short_link.post.collection.domain.ConnectionBlockType;
import com.example.short_link.post.collection.domain.DiscoverConnectionRow;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaCollectionConnectionRepository
    extends JpaRepository<CollectionConnectionEntity, Long> {

  List<CollectionConnectionEntity> findAllByCollectionIdOrderByPositionAsc(Long collectionId);

  List<CollectionConnectionEntity> findAllByCollectionIdInOrderByPositionDesc(
      Collection<Long> collectionIds);

  @Query(
      """
      select new com.example.short_link.post.collection.domain.DiscoverConnectionRow(
          c.id, c.blockType, c.refId, c.why, c.createdAt, col.id, col.title, col.kind, col.ownerId)
      from CollectionConnectionEntity c, CollectionEntity col
      where c.collectionId = col.id
        and col.ownerId in :ownerIds
        and col.visibility = com.example.short_link.post.collection.domain.CollectionVisibility.PUBLIC
      order by c.createdAt desc
      """)
  List<DiscoverConnectionRow> findPublicConnectionsByOwners(
      @Param("ownerIds") Collection<Long> ownerIds, Pageable pageable);

  long countByCollectionId(Long collectionId);

  boolean existsByCollectionIdAndBlockTypeAndRefId(
      Long collectionId, ConnectionBlockType blockType, Long refId);

  List<CollectionConnectionEntity> findAllByBlockTypeAndRefId(
      ConnectionBlockType blockType, Long refId);

  @Query(
      "select max(c.position) from CollectionConnectionEntity c where c.collectionId = :collectionId")
  Integer findMaxPositionByCollectionId(@Param("collectionId") Long collectionId);

  // 공동 등장 — 이 블록이 놓인 *공개* 컬렉션에 함께 놓인 다른 블록들(자기 자신 제외).
  // 사람이 손으로 엮은 간선만 — sharedCount 큰 순 = 같은 길에 더 자주 함께 놓인 것. §0: PUBLIC 만.
  @Query(
      value =
          "SELECT cc2.block_type AS blockType, cc2.ref_id AS refId, "
              + "COUNT(DISTINCT cc1.collection_id) AS sharedCount "
              + "FROM collection_connection cc1 "
              + "JOIN collection col ON col.id = cc1.collection_id AND col.visibility = 'PUBLIC' "
              + "JOIN collection_connection cc2 ON cc2.collection_id = cc1.collection_id "
              + "WHERE cc1.block_type = :blockType AND cc1.ref_id = :refId "
              + "AND NOT (cc2.block_type = :blockType AND cc2.ref_id = :refId) "
              + "GROUP BY cc2.block_type, cc2.ref_id "
              + "ORDER BY sharedCount DESC, MAX(cc2.created_at) DESC "
              + "LIMIT :limit",
      nativeQuery = true)
  List<
          com.example.short_link.post.collection.domain.repository.projection
              .CurationGraphProjections.CooccurrenceRow>
      findCooccurring(
          @Param("blockType") String blockType,
          @Param("refId") Long refId,
          @Param("limit") int limit);

  // 큐레이터 겹침 — 내 공개 컬렉션의 블록을 자기 공개 컬렉션에도 엮은 다른 큐레이터(나 제외).
  // sharedItems = (block_type, ref_id) 쌍 기준 겹치는 블록 수 — 팔로우가 아니라 취향(엮은 것)으로 잇는다.
  @Query(
      value =
          "SELECT col2.owner_id AS curatorId, "
              + "COUNT(DISTINCT cc1.block_type, cc1.ref_id) AS sharedItems "
              + "FROM collection_connection cc1 "
              + "JOIN collection col1 ON col1.id = cc1.collection_id "
              + "AND col1.owner_id = :ownerId AND col1.visibility = 'PUBLIC' "
              + "JOIN collection_connection cc2 "
              + "ON cc2.block_type = cc1.block_type AND cc2.ref_id = cc1.ref_id "
              + "JOIN collection col2 ON col2.id = cc2.collection_id "
              + "AND col2.visibility = 'PUBLIC' AND col2.owner_id <> :ownerId "
              + "GROUP BY col2.owner_id "
              + "ORDER BY sharedItems DESC "
              + "LIMIT :limit",
      nativeQuery = true)
  List<
          com.example.short_link.post.collection.domain.repository.projection
              .CurationGraphProjections.CuratorOverlapRow>
      findOverlappingCurators(@Param("ownerId") Long ownerId, @Param("limit") int limit);
}
