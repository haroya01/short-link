package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.post.domain.SeriesEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaSeriesRepository extends JpaRepository<SeriesEntity, Long> {

  Optional<SeriesEntity> findByUserIdAndSlug(Long userId, String slug);

  boolean existsByUserIdAndSlug(Long userId, String slug);

  List<SeriesEntity> findAllByUserIdOrderByCreatedAtDesc(Long userId);
}
