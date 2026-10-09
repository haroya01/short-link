package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.common.post.SeriesItemCleaner;
import com.example.short_link.post.domain.SeriesItemType;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class SeriesItemCleanerAdapter implements SeriesItemCleaner {

  private final JpaSeriesItemRepository jpa;

  @Override
  public void purgeForNote(long noteId) {
    jpa.deleteByRefs(SeriesItemType.NOTE, List.of(noteId));
  }
}
