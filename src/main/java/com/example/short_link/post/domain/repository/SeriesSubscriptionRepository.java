package com.example.short_link.post.domain.repository;

import com.example.short_link.post.domain.SeriesSubscriptionEntity;
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
}
