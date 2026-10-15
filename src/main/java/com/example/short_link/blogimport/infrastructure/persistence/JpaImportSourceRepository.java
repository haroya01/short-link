package com.example.short_link.blogimport.infrastructure.persistence;

import com.example.short_link.blogimport.domain.ImportSourceEntity;
import com.example.short_link.blogimport.domain.ImportSourceStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface JpaImportSourceRepository extends JpaRepository<ImportSourceEntity, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from ImportSourceEntity s where s.id = :id")
  Optional<ImportSourceEntity> findByIdForUpdate(@Param("id") Long id);

  Optional<ImportSourceEntity> findByUserIdAndBlogKey(Long userId, String blogKey);

  boolean existsByClaimKeyAndUserIdNot(String claimKey, Long userId);

  List<ImportSourceEntity> findAllByUserIdOrderByIdDesc(Long userId);

  long countByUserIdAndStatusNot(Long userId, ImportSourceStatus status);

  @Query(
      "select s.id from ImportSourceEntity s"
          + " where (s.status in :syncing"
          + " and (s.nextFetchAt is null or s.nextFetchAt <= :now))"
          + " or (s.status = :importing and s.updatedAt < :stalledBefore)"
          + " order by s.nextFetchAt asc, s.id asc")
  List<Long> findDueIds(
      @Param("syncing") List<ImportSourceStatus> syncing,
      @Param("importing") ImportSourceStatus importing,
      @Param("now") Instant now,
      @Param("stalledBefore") Instant stalledBefore,
      Pageable pageable);
}
