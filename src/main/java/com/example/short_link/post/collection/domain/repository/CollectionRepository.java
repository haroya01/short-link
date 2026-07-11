package com.example.short_link.post.collection.domain.repository;

import com.example.short_link.post.collection.domain.CollectionEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CollectionRepository {

  CollectionEntity save(CollectionEntity collection);

  Optional<CollectionEntity> findById(Long id);

  /** 여러 컬렉션을 한 쿼리로 — findById 를 컬렉션 수만큼 반복하지 않게(N+1 방지). ids 가 비면 빈 목록. */
  List<CollectionEntity> findAllByIdIn(Collection<Long> ids);

  void delete(CollectionEntity collection);

  /** A user's own collections, most recently touched first — the "my collections" list. */
  List<CollectionEntity> findAllByOwnerIdOrderByUpdatedAtDesc(Long ownerId);
}
