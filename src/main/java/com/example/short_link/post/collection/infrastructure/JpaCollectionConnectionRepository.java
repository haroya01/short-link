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

  @Query(
      """
      select new com.example.short_link.post.collection.domain.DiscoverConnectionRow(
          c.id, c.blockType, c.refId, c.why, c.createdAt, col.id, col.title, col.ownerId)
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

  @Query(
      "select max(c.position) from CollectionConnectionEntity c where c.collectionId = :collectionId")
  Integer findMaxPositionByCollectionId(@Param("collectionId") Long collectionId);
}
