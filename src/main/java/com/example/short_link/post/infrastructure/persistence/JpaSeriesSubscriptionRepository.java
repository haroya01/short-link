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

  // Returns [seriesId, count]; series with zero subscribers are absent.
  @Query(
      "select s.seriesId, count(s) from SeriesSubscriptionEntity s "
          + "where s.seriesId in :seriesIds group by s.seriesId")
  List<Object[]> countBySeriesIdIn(@Param("seriesIds") Collection<Long> seriesIds);

  @Query("select s.seriesId from SeriesSubscriptionEntity s where s.userId = :userId")
  List<Long> findSubscribedSeriesIds(@Param("userId") Long userId);

  // INSERT IGNORE avoids duplicate exceptions. Native inserts bypass @CreationTimestamp,
  // so created_at is set explicitly.
  @Modifying
  @Query(
      value =
          "INSERT IGNORE INTO series_subscription (user_id, series_id, created_at) "
              + "VALUES (:userId, :seriesId, NOW())",
      nativeQuery = true)
  int insertIgnore(@Param("userId") Long userId, @Param("seriesId") Long seriesId);

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
