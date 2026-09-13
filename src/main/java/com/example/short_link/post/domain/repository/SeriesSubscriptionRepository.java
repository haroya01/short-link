package com.example.short_link.post.domain.repository;

import com.example.short_link.post.domain.DailyViewCount;
import com.example.short_link.post.domain.SeriesSubscriptionEntity;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface SeriesSubscriptionRepository {

  boolean existsByUserIdAndSeriesId(Long userId, Long seriesId);

  Optional<SeriesSubscriptionEntity> findByUserIdAndSeriesId(Long userId, Long seriesId);

  void delete(SeriesSubscriptionEntity subscription);

  /**
   * Returns 1 for a new subscription or 0 for a duplicate, so notification fires exactly once.
   * Duplicate inserts must not fail the transaction.
   */
  int insertIgnore(Long userId, Long seriesId);

  long countBySeriesId(Long seriesId);

  /** Maps series ID to subscriber count; series with zero subscribers are absent. */
  Map<Long, Long> countBySeriesIdIn(Collection<Long> seriesIds);

  List<Long> findSubscribedSeriesIds(Long userId);

  /** Sparse new-subscriber counts per UTC day. {@link DailyViewCount#views()} carries the count. */
  List<DailyViewCount> countDailyBySeriesIdSince(Long seriesId, Instant since);
}
