package com.example.short_link.post.domain.repository;

import com.example.short_link.post.domain.SeriesEntity;
import java.util.List;
import java.util.Optional;

public interface SeriesRepository {

  Optional<SeriesEntity> findById(Long id);

  Optional<SeriesEntity> findByUserIdAndSlug(Long userId, String slug);

  SeriesEntity save(SeriesEntity series);

  void delete(SeriesEntity series);

  boolean existsByUserIdAndSlug(Long userId, String slug);

  List<SeriesEntity> findAllByUserIdOrderByCreatedAtDesc(Long userId);
}
