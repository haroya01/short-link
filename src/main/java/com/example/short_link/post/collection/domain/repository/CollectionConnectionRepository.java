package com.example.short_link.post.collection.domain.repository;

import com.example.short_link.post.collection.domain.CollectionConnectionEntity;
import com.example.short_link.post.collection.domain.ConnectionBlockType;
import java.util.List;
import java.util.Optional;

public interface CollectionConnectionRepository {

  CollectionConnectionEntity save(CollectionConnectionEntity connection);

  Optional<CollectionConnectionEntity> findById(Long id);

  void delete(CollectionConnectionEntity connection);

  /** A collection's connections in curator order. */
  List<CollectionConnectionEntity> findAllByCollectionIdOrderByPositionAsc(Long collectionId);

  long countByCollectionId(Long collectionId);

  boolean existsByCollectionIdAndBlockTypeAndRefId(
      Long collectionId, ConnectionBlockType blockType, Long refId);

  /** Highest position in a collection (for append), or null when empty. */
  Integer findMaxPositionByCollectionId(Long collectionId);
}
