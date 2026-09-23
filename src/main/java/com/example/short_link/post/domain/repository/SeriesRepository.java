package com.example.short_link.post.domain.repository;

import com.example.short_link.post.domain.SeriesEntity;
import com.example.short_link.post.domain.SeriesSummary;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SeriesRepository {

  Optional<SeriesEntity> findById(Long id);

  /** Serializes membership changes and deletion before any member post is locked. */
  Optional<SeriesEntity> findByIdForUpdate(Long id);

  List<SeriesEntity> findAllByIdIn(Collection<Long> ids);

  Optional<SeriesEntity> findByUserIdAndSlug(Long userId, String slug);

  SeriesEntity save(SeriesEntity series);

  void delete(SeriesEntity series);

  boolean existsByUserIdAndSlug(Long userId, String slug);

  List<SeriesEntity> findAllByUserIdOrderByCreatedAtDesc(Long userId);

  List<SeriesSummary> findPublishedSummaries(Collection<Long> seriesIds);
}
