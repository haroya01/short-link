package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.post.domain.SeriesSubscriptionEntity;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaSeriesSubscriptionRepository
    extends JpaRepository<SeriesSubscriptionEntity, Long> {

  boolean existsByUserIdAndSeriesId(Long userId, Long seriesId);

  Optional<SeriesSubscriptionEntity> findByUserIdAndSeriesId(Long userId, Long seriesId);

  long countBySeriesId(Long seriesId);

  @Query("select s.seriesId from SeriesSubscriptionEntity s where s.userId = :userId")
  List<Long> findSubscribedSeriesIds(@Param("userId") Long userId);

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
