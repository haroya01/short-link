package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.post.domain.PostStatus;
import com.example.short_link.post.domain.SeriesEntity;
import com.example.short_link.post.domain.SeriesSummary;
import com.example.short_link.post.domain.repository.SeriesRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class SeriesRepositoryAdapter implements SeriesRepository {

  private final JpaSeriesRepository jpa;

  @Override
  public Optional<SeriesEntity> findById(Long id) {
    return jpa.findById(id);
  }

  @Override
  public Optional<SeriesEntity> findByIdForUpdate(Long id) {
    return jpa.findByIdForUpdate(id);
  }

  @Override
  public List<SeriesEntity> findAllByIdIn(Collection<Long> ids) {
    return jpa.findAllByIdIn(ids);
  }

  @Override
  public Optional<SeriesEntity> findByUserIdAndSlug(Long userId, String slug) {
    return jpa.findByUserIdAndSlug(userId, slug);
  }

  @Override
  public SeriesEntity save(SeriesEntity series) {
    return jpa.save(series);
  }

  @Override
  public void delete(SeriesEntity series) {
    jpa.delete(series);
  }

  @Override
  public boolean existsByUserIdAndSlug(Long userId, String slug) {
    return jpa.existsByUserIdAndSlug(userId, slug);
  }

  @Override
  public List<SeriesEntity> findAllByUserIdOrderByCreatedAtDesc(Long userId) {
    return jpa.findAllByUserIdOrderByCreatedAtDesc(userId);
  }

  @Override
  public List<SeriesSummary> findPublishedSummaries(Collection<Long> seriesIds) {
    if (seriesIds.isEmpty()) return List.of();
    return jpa.findPublishedSummaries(seriesIds, PostStatus.PUBLISHED).stream()
        .map(
            row ->
                new SeriesSummary(
                    ((Number) row[0]).longValue(),
                    (String) row[1],
                    (String) row[2],
                    ((Number) row[3]).longValue()))
        .toList();
  }
}
