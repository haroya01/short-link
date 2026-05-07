package com.example.short_link.link.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LinkRepository extends JpaRepository<LinkEntity, Long> {

  boolean existsByShortCode(String shortCode);

  Optional<LinkEntity> findByShortCode(String shortCode);

  List<LinkEntity> findAllByUserIdOrderByCreatedAtDesc(Long userId);

  Page<LinkEntity> findAllByUserId(Long userId, Pageable pageable);

  @Query(
      "SELECT l FROM LinkEntity l WHERE l.userId = :userId "
          + "AND (LOWER(l.originalUrl) LIKE LOWER(CONCAT('%', :q, '%')) "
          + "OR LOWER(l.shortCode) LIKE LOWER(CONCAT('%', :q, '%')))")
  Page<LinkEntity> searchByUserId(
      @Param("userId") Long userId, @Param("q") String q, Pageable pageable);

  long countByCreatedAtAfter(Instant since);

  long countByUserIdIsNull();

  long countByExpiresAtBefore(Instant when);
}
