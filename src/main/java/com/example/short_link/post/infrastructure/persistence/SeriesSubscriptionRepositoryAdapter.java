package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.post.domain.DailyViewCount;
import com.example.short_link.post.domain.SeriesSubscriptionEntity;
import com.example.short_link.post.domain.repository.SeriesSubscriptionRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class SeriesSubscriptionRepositoryAdapter implements SeriesSubscriptionRepository {

  private final JpaSeriesSubscriptionRepository jpa;

  @Override
  public boolean existsByUserIdAndSeriesId(Long userId, Long seriesId) {
    return jpa.existsByUserIdAndSeriesId(userId, seriesId);
  }

  @Override
  public Optional<SeriesSubscriptionEntity> findByUserIdAndSeriesId(Long userId, Long seriesId) {
    return jpa.findByUserIdAndSeriesId(userId, seriesId);
  }

  @Override
  public void delete(SeriesSubscriptionEntity subscription) {
    jpa.delete(subscription);
  }

  @Override
  public int insertIgnore(Long userId, Long seriesId) {
    return jpa.insertIgnore(userId, seriesId);
  }

  @Override
  public long countBySeriesId(Long seriesId) {
    return jpa.countBySeriesId(seriesId);
  }

  @Override
  public Map<Long, Long> countBySeriesIdIn(Collection<Long> seriesIds) {
    if (seriesIds.isEmpty()) {
      return Map.of();
    }
    return jpa.countBySeriesIdIn(seriesIds).stream()
        .collect(Collectors.toMap(r -> (Long) r[0], r -> (Long) r[1]));
  }

  @Override
  public List<Long> findSubscribedSeriesIds(Long userId) {
    return jpa.findSubscribedSeriesIds(userId);
  }

  @Override
  public List<DailyViewCount> countDailyBySeriesIdSince(Long seriesId, Instant since) {
    return jpa.countDailyBySeriesIdSince(seriesId, since).stream()
        .map(r -> new DailyViewCount(r.getDay(), r.getCount()))
        .toList();
  }
}
