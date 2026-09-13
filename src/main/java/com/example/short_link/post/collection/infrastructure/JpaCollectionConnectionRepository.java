package com.example.short_link.post.collection.infrastructure;

import com.example.short_link.post.collection.domain.CollectionConnectionCount;
import com.example.short_link.post.collection.domain.CollectionConnectionEntity;
import com.example.short_link.post.collection.domain.ConnectionBlockType;
import com.example.short_link.post.collection.domain.DiscoverConnectionRow;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

  @Query(
      """
      select new com.example.short_link.post.collection.domain.DiscoverConnectionRow(
          c.id, c.blockType, c.refId, c.why, c.createdAt, col.id, col.title, col.kind, col.ownerId)
      from CollectionConnectionEntity c, CollectionEntity col
      where c.collectionId = col.id
        and col.visibility = com.example.short_link.post.collection.domain.CollectionVisibility.PUBLIC
      order by c.createdAt desc
      """)
  List<DiscoverConnectionRow> findRecentPublicConnections(Pageable pageable);

  long countByCollectionId(Long collectionId);

  List<CollectionConnectionEntity> findAllByBlockTypeAndRefId(
      ConnectionBlockType blockType, Long refId);

  List<CollectionConnectionEntity> findAllByBlockTypeAndRefIdIn(
      ConnectionBlockType blockType, Collection<Long> refIds);

  @Query(
      """
      select new com.example.short_link.post.collection.domain.CollectionConnectionCount(
          c.collectionId, count(c))
      from CollectionConnectionEntity c
      where c.collectionId in :collectionIds
      group by c.collectionId
      """)
  List<CollectionConnectionCount> countByCollectionIdIn(
      @Param("collectionIds") Collection<Long> collectionIds);

  /** 저장된 position에 빈자리가 있어도 (position, id) 정렬로 연속된 1-based 순위를 계산한다. */
  @Query(
      value =
          "SELECT collection_id AS collectionId, ref_id AS refId, "
              + "ROW_NUMBER() OVER ("
              + "  PARTITION BY collection_id ORDER BY position ASC, id ASC) AS position "
              + "FROM collection_connection "
              + "WHERE collection_id IN :collectionIds AND block_type = :blockType",
      nativeQuery = true)
  List<ConnectionRankProjection> findRanksByCollectionIdInAndBlockType(
      @Param("collectionIds") Collection<Long> collectionIds, @Param("blockType") String blockType);

  interface ConnectionRankProjection {
    Long getCollectionId();

    Long getRefId();

    int getPosition();
  }

  /** FK 없는 다형 참조를 정리한다. 벌크 삭제가 영속성 컨텍스트를 우회하므로 삭제 트랜잭션 안에서만 호출한다. */
  @Modifying
  @Query(
      "delete from CollectionConnectionEntity c"
          + " where c.blockType = :blockType and c.refId in :refIds")
  int deleteByBlockTypeAndRefIdIn(
      @Param("blockType") ConnectionBlockType blockType, @Param("refIds") Collection<Long> refIds);

  // PUBLIC 컬렉션에서만 공동 등장을 집계하며 대상 블록 자신은 제외한다.
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

  // sharedItems는 중복을 제거한 (block_type, ref_id) 쌍의 수다.
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
