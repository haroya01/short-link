package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.post.domain.SeriesSubscriptionEntity;
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
}
