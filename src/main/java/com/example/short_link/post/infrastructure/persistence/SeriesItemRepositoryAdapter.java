package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.post.domain.SeriesItemEntity;
import com.example.short_link.post.domain.SeriesItemType;
import com.example.short_link.post.domain.repository.SeriesItemRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class SeriesItemRepositoryAdapter implements SeriesItemRepository {

  private final JpaSeriesItemRepository jpa;

  @Override
  public List<SeriesItemEntity> findBySeriesId(Long seriesId) {
    return jpa.findAllBySeriesIdOrderByPositionAscIdAsc(seriesId);
  }

  @Override
  public List<SeriesItemEntity> findBySeriesIdIn(Collection<Long> seriesIds) {
    if (seriesIds.isEmpty()) {
      return List.of();
    }
    return jpa.findAllBySeriesIdInOrderBySeriesIdAscPositionAscIdAsc(seriesIds);
  }

  @Override
  public void replace(Long seriesId, List<SeriesItemEntity> items) {
    jpa.deleteBySeriesId(seriesId);
    Map<SeriesItemType, List<Long>> refs =
        items.stream()
            .collect(
                Collectors.groupingBy(
                    SeriesItemEntity::getType,
                    Collectors.mapping(SeriesItemEntity::getRefId, Collectors.toList())));
    refs.forEach(jpa::deleteByRefs);
    jpa.saveAll(items);
  }

  @Override
  public void deleteBySeriesId(Long seriesId) {
    jpa.deleteBySeriesId(seriesId);
  }

  @Override
  public void deleteByRef(SeriesItemType type, Long refId) {
    jpa.deleteByRefs(type, List.of(refId));
  }
}
