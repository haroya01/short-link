package com.example.short_link.post.domain.repository;

import com.example.short_link.post.domain.SeriesEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SeriesRepository {

  Optional<SeriesEntity> findById(Long id);

  /** Batch hydrate series by id — used to turn ranked series ids back into entities without N+1. */
  List<SeriesEntity> findAllByIdIn(Collection<Long> ids);

  Optional<SeriesEntity> findByUserIdAndSlug(Long userId, String slug);

  SeriesEntity save(SeriesEntity series);

  void delete(SeriesEntity series);

  boolean existsByUserIdAndSlug(Long userId, String slug);

  List<SeriesEntity> findAllByUserIdOrderByCreatedAtDesc(Long userId);
}
