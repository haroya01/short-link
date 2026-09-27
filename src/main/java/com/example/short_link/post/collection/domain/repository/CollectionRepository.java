package com.example.short_link.post.collection.domain.repository;

import com.example.short_link.post.collection.domain.CollectionEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CollectionRepository {

  CollectionEntity save(CollectionEntity collection);

  Optional<CollectionEntity> findById(Long id);

  List<CollectionEntity> findAllByIdIn(Collection<Long> ids);

  void delete(CollectionEntity collection);

  List<CollectionEntity> findAllByOwnerIdOrderByUpdatedAtDesc(Long ownerId);
}
