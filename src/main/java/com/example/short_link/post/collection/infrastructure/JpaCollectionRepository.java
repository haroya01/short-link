package com.example.short_link.post.collection.infrastructure;

import com.example.short_link.post.collection.domain.CollectionEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaCollectionRepository extends JpaRepository<CollectionEntity, Long> {

  List<CollectionEntity> findAllByOwnerIdOrderByUpdatedAtDesc(Long ownerId);

  List<CollectionEntity> findAllByIdIn(Collection<Long> ids);
}
