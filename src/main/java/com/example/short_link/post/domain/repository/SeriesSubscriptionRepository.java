package com.example.short_link.post.domain.repository;

import com.example.short_link.post.domain.DailyViewCount;
import com.example.short_link.post.domain.SeriesSubscriptionEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SeriesSubscriptionRepository {

  boolean existsByUserIdAndSeriesId(Long userId, Long seriesId);

  Optional<SeriesSubscriptionEntity> findByUserIdAndSeriesId(Long userId, Long seriesId);

  SeriesSubscriptionEntity save(SeriesSubscriptionEntity subscription);

  void delete(SeriesSubscriptionEntity subscription);

  /** How many users subscribe to this series. */
  long countBySeriesId(Long seriesId);

  /** Ids of every series this user subscribes to — feeds the "following" tab + the card's state. */
  List<Long> findSubscribedSeriesIds(Long userId);

  /**
   * New subscribers per UTC day since {@code since} (sparse) — drives the subscriber trend chart.
   * The {@code views} field of each {@link DailyViewCount} carries that day's new-subscriber count.
   */
  List<DailyViewCount> countDailyBySeriesIdSince(Long seriesId, Instant since);
}
