package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.post.domain.SeriesEntity;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaSeriesRepository extends JpaRepository<SeriesEntity, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from SeriesEntity s where s.id = :id")
  Optional<SeriesEntity> findByIdForUpdate(@Param("id") Long id);

  List<SeriesEntity> findAllByIdIn(Collection<Long> ids);

  Optional<SeriesEntity> findByUserIdAndSlug(Long userId, String slug);

  boolean existsByUserIdAndSlug(Long userId, String slug);

  List<SeriesEntity> findAllByUserIdOrderByCreatedAtDesc(Long userId);
}
