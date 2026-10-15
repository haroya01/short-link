package com.example.short_link.blogimport.infrastructure.persistence;

import com.example.short_link.blogimport.domain.ImportItemEntity;
import com.example.short_link.blogimport.domain.repository.ImportItemRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class ImportItemRepositoryAdapter implements ImportItemRepository {

  private final JpaImportItemRepository jpa;

  @Override
  public List<ImportItemEntity> findBySourceIdAndUrlHashIn(
      Long sourceId, Collection<String> urlHashes) {
    if (urlHashes.isEmpty()) {
      return List.of();
    }
    return jpa.findBySourceIdAndUrlHashIn(sourceId, urlHashes);
  }

  @Override
  public Optional<ImportItemEntity> findById(Long id) {
    return jpa.findById(id);
  }

  @Override
  public ImportItemEntity saveAndFlush(ImportItemEntity item) {
    return jpa.saveAndFlush(item);
  }
}
