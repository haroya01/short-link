package com.example.short_link.blogimport.domain.repository;

import com.example.short_link.blogimport.domain.ImportItemEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ImportItemRepository {

  List<ImportItemEntity> findBySourceIdAndUrlHashIn(Long sourceId, Collection<String> urlHashes);

  Optional<ImportItemEntity> findById(Long id);

  // Writes now, so a second run taking the same original fails here as a
  // DataIntegrityViolationException.
  ImportItemEntity saveAndFlush(ImportItemEntity item);
}
