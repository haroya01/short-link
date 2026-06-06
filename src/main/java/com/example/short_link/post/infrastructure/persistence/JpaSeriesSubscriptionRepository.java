package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.post.domain.SeriesSubscriptionEntity;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaSeriesSubscriptionRepository
    extends JpaRepository<SeriesSubscriptionEntity, Long> {

  boolean existsByUserIdAndSeriesId(Long userId, Long seriesId);

  Optional<SeriesSubscriptionEntity> findByUserIdAndSeriesId(Long userId, Long seriesId);

  long countBySeriesId(Long seriesId);

  // Subscriber counts for many series in one grouped query — avoids a per-series countBySeriesId in
  // the analytics list. Returns [seriesId, count]; series with zero subscribers don't appear, so
  // the
  // caller defaults them to 0.
  @Query(
      "select s.seriesId, count(s) from SeriesSubscriptionEntity s "
          + "where s.seriesId in :seriesIds group by s.seriesId")
  List<Object[]> countBySeriesIdIn(@Param("seriesIds") Collection<Long> seriesIds);

  @Query("select s.seriesId from SeriesSubscriptionEntity s where s.userId = :userId")
  List<Long> findSubscribedSeriesIds(@Param("userId") Long userId);

  // MySQL INSERT IGNORE: the (user_id, series_id) unique key turns a duplicate subscription into a
  // no-op (0 rows) instead of a constraint violation, so the subscribe flow stays idempotent
  // without
  // catching an exception inside the transaction. created_at is set here because the native insert
  // bypasses the @CreationTimestamp callback.
  @Modifying
  @Query(
      value =
          "INSERT IGNORE INTO series_subscription (user_id, series_id, created_at) "
              + "VALUES (:userId, :seriesId, NOW())",
      nativeQuery = true)
  int insertIgnore(@Param("userId") Long userId, @Param("seriesId") Long seriesId);

  /** Projection for the GROUP BY DATE(created_at) aggregation — alias names map to the getters. */
  interface DailySubRow {
    LocalDate getDay();

    long getCount();
  }

  @Query(
      nativeQuery = true,
      value =
          "SELECT DATE(s.created_at) AS day, COUNT(*) AS count "
              + "FROM series_subscription s "
              + "WHERE s.series_id = :seriesId AND s.created_at >= :since "
              + "GROUP BY DATE(s.created_at) "
              + "ORDER BY day")
  List<DailySubRow> countDailyBySeriesIdSince(
      @Param("seriesId") Long seriesId, @Param("since") Instant since);
}
