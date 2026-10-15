package com.example.short_link.blogimport.infrastructure.persistence;

import com.example.short_link.blogimport.domain.ImportItemEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface JpaImportItemRepository extends JpaRepository<ImportItemEntity, Long> {

  List<ImportItemEntity> findBySourceIdAndUrlHashIn(Long sourceId, Collection<String> urlHashes);
}
