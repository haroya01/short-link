package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.post.domain.SeriesItemEntity;
import com.example.short_link.post.domain.SeriesItemType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaSeriesItemRepository extends JpaRepository<SeriesItemEntity, Long> {

  List<SeriesItemEntity> findAllBySeriesIdOrderByPositionAscIdAsc(Long seriesId);

  List<SeriesItemEntity> findAllBySeriesIdInOrderBySeriesIdAscPositionAscIdAsc(
      Collection<Long> seriesIds);

  @Modifying(flushAutomatically = true)
  @Query("delete from SeriesItemEntity i where i.seriesId = :seriesId")
  void deleteBySeriesId(@Param("seriesId") Long seriesId);

  @Modifying(flushAutomatically = true)
  @Query("delete from SeriesItemEntity i where i.type = :type and i.refId in :refIds")
  void deleteByRefs(@Param("type") SeriesItemType type, @Param("refIds") Collection<Long> refIds);
}
