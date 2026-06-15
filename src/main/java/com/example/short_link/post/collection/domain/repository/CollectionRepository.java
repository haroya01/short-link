package com.example.short_link.post.collection.domain.repository;

import com.example.short_link.post.collection.domain.CollectionEntity;
import java.util.List;
import java.util.Optional;

public interface CollectionRepository {

  CollectionEntity save(CollectionEntity collection);

  Optional<CollectionEntity> findById(Long id);

  void delete(CollectionEntity collection);

  /** A user's own collections, most recently touched first — the "my collections" list. */
  List<CollectionEntity> findAllByOwnerIdOrderByUpdatedAtDesc(Long ownerId);
}
