package com.example.short_link.blogimport.domain.repository;

import com.example.short_link.blogimport.domain.ImportSourceEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ImportSourceRepository {

  ImportSourceEntity save(ImportSourceEntity source);

  // Writes now, so a claim another member holds fails here as a DataIntegrityViolationException.
  ImportSourceEntity saveAndFlush(ImportSourceEntity source);

  Optional<ImportSourceEntity> findById(Long id);

  Optional<ImportSourceEntity> findByIdForUpdate(Long id);

  Optional<ImportSourceEntity> findByUserIdAndBlogKey(Long userId, String blogKey);

  boolean isClaimedByAnother(String blogKey, Long userId);

  List<ImportSourceEntity> findAllByUserId(Long userId);

  long countActiveByUserId(Long userId);

  // Verified sources not synced since syncedBefore, and first imports stalled since stalledBefore.
  List<Long> findDueIds(Instant syncedBefore, Instant stalledBefore, int limit);

  void delete(ImportSourceEntity source);
}
