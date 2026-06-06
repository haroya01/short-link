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
   * Insert a subscription, ignoring the duplicate-key conflict when the user already subscribes.
   * Returns the number of rows inserted (1 = newly subscribed, 0 = already subscribed) so the
   * caller fires the new-subscriber notification exactly once. Atomic — no read-then-write race,
   * and no exception to poison the surrounding transaction.
   */
  int insertIgnore(Long userId, Long seriesId);

  /** How many users subscribe to this series. */
  long countBySeriesId(Long seriesId);

  /**
   * Subscriber counts for many series at once, keyed by series id — the batch form of {@link
   * #countBySeriesId} for the series-analytics list. Series with no subscribers are absent from the
   * map (caller defaults to 0).
   */
  Map<Long, Long> countBySeriesIdIn(Collection<Long> seriesIds);

  /** Ids of every series this user subscribes to — feeds the "following" tab + the card's state. */
  List<Long> findSubscribedSeriesIds(Long userId);

  /**
   * New subscribers per UTC day since {@code since} (sparse) — drives the subscriber trend chart.
   * The {@code views} field of each {@link DailyViewCount} carries that day's new-subscriber count.
   */
  List<DailyViewCount> countDailyBySeriesIdSince(Long seriesId, Instant since);
}
