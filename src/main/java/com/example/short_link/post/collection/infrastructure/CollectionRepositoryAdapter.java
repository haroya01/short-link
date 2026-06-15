package com.example.short_link.post.collection.infrastructure;

import com.example.short_link.post.collection.domain.CollectionEntity;
import com.example.short_link.post.collection.domain.repository.CollectionRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class CollectionRepositoryAdapter implements CollectionRepository {

  private final JpaCollectionRepository jpa;

  @Override
  public CollectionEntity save(CollectionEntity collection) {
    return jpa.save(collection);
  }

  @Override
  public Optional<CollectionEntity> findById(Long id) {
    return jpa.findById(id);
  }

  @Override
  public void delete(CollectionEntity collection) {
    jpa.delete(collection);
  }

  @Override
  public List<CollectionEntity> findAllByOwnerIdOrderByUpdatedAtDesc(Long ownerId) {
    return jpa.findAllByOwnerIdOrderByUpdatedAtDesc(ownerId);
  }
}
