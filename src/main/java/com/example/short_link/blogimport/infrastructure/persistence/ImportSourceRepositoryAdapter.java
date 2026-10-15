package com.example.short_link.blogimport.infrastructure.persistence;

import com.example.short_link.blogimport.domain.ImportSourceEntity;
import com.example.short_link.blogimport.domain.ImportSourceStatus;
import com.example.short_link.blogimport.domain.repository.ImportSourceRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class ImportSourceRepositoryAdapter implements ImportSourceRepository {

  private final JpaImportSourceRepository jpa;

  @Override
  public ImportSourceEntity save(ImportSourceEntity source) {
    return jpa.save(source);
  }

  @Override
  public ImportSourceEntity saveAndFlush(ImportSourceEntity source) {
    return jpa.saveAndFlush(source);
  }

  @Override
  public Optional<ImportSourceEntity> findById(Long id) {
    return jpa.findById(id);
  }

  @Override
  public Optional<ImportSourceEntity> findByIdForUpdate(Long id) {
    return jpa.findByIdForUpdate(id);
  }

  @Override
  public Optional<ImportSourceEntity> findByUserIdAndBlogKey(Long userId, String blogKey) {
    return jpa.findByUserIdAndBlogKey(userId, blogKey);
  }

  @Override
  public boolean isClaimedByAnother(String blogKey, Long userId) {
    return jpa.existsByClaimKeyAndUserIdNot(blogKey, userId);
  }

  @Override
  public List<ImportSourceEntity> findAllByUserId(Long userId) {
    return jpa.findAllByUserIdOrderByIdDesc(userId);
  }

  @Override
  public long countActiveByUserId(Long userId) {
    return jpa.countByUserIdAndStatusNot(userId, ImportSourceStatus.DISCONNECTED);
  }

  @Override
  public List<Long> findDueIds(Instant syncedBefore, Instant stalledBefore, int limit) {
    return jpa.findDueIds(
        List.of(ImportSourceStatus.SYNCING, ImportSourceStatus.ERROR),
        ImportSourceStatus.IMPORTING,
        syncedBefore,
        stalledBefore,
        PageRequest.of(0, limit));
  }

  @Override
  public void delete(ImportSourceEntity source) {
    jpa.delete(source);
  }
}
