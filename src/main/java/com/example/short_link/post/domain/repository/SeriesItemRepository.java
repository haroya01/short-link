package com.example.short_link.post.domain.repository;

import com.example.short_link.post.domain.SeriesItemEntity;
import com.example.short_link.post.domain.SeriesItemType;
import java.util.Collection;
import java.util.List;

public interface SeriesItemRepository {

  List<SeriesItemEntity> findBySeriesId(Long seriesId);

  List<SeriesItemEntity> findBySeriesIdIn(Collection<Long> seriesIds);

  // Replaces the series' items and frees the given refs from any other series first, so a post or
  // a note moves rather than sits in two series.
  void replace(Long seriesId, List<SeriesItemEntity> items);

  void deleteBySeriesId(Long seriesId);

  void deleteByRef(SeriesItemType type, Long refId);
}
