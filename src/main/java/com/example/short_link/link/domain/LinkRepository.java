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

  @Query(
      "SELECT l FROM LinkEntity l "
          + "WHERE l.userId = :userId AND l.id IN "
          + "(SELECT lt.linkId FROM LinkTagEntity lt JOIN TagEntity t ON t.id = lt.tagId "
          + "WHERE t.userId = :userId AND t.name = :tagName)")
  Page<LinkEntity> findByUserIdAndTagName(
      @Param("userId") Long userId, @Param("tagName") String tagName, Pageable pageable);

  @Query(
      "SELECT l FROM LinkEntity l "
          + "WHERE l.userId = :userId "
          + "AND (LOWER(l.originalUrl) LIKE LOWER(CONCAT('%', :q, '%')) "
          + "OR LOWER(l.shortCode) LIKE LOWER(CONCAT('%', :q, '%'))) "
          + "AND l.id IN "
          + "(SELECT lt.linkId FROM LinkTagEntity lt JOIN TagEntity t ON t.id = lt.tagId "
          + "WHERE t.userId = :userId AND t.name = :tagName)")
  Page<LinkEntity> searchByUserIdAndTagName(
      @Param("userId") Long userId,
      @Param("q") String q,
      @Param("tagName") String tagName,
      Pageable pageable);

  long countByCreatedAtAfter(Instant since);

  long countByUserIdIsNull();

  long countByExpiresAtBefore(Instant when);

  List<LinkEntity> findTop500ByExpiresAtBeforeOrderByExpiresAtAsc(Instant when);

  @Query(
      "SELECT l FROM LinkEntity l "
          + "WHERE l.ogFetchStatus IN ('PENDING','RETRYABLE') "
          + "AND l.ogFetchAttempts < :maxAttempts "
          + "AND (l.ogFetchedAt IS NULL OR l.ogFetchedAt < :before)")
  List<LinkEntity> findOgRetryCandidates(
      @Param("maxAttempts") int maxAttempts, @Param("before") Instant before, Pageable pageable);

  @Query(
      "SELECT l FROM LinkEntity l "
          + "WHERE l.ogFetchStatus = 'OK' "
          + "AND l.ogFetchedAt IS NOT NULL "
          + "AND l.ogFetchedAt < :before")
  List<LinkEntity> findStaleOgCandidates(@Param("before") Instant before, Pageable pageable);

  long countByUserId(Long userId);

  Optional<LinkEntity> findFirstByUserIdAndOriginalUrl(Long userId, String originalUrl);

  List<LinkEntity> findAllByClaimTokenInAndUserIdIsNull(java.util.Collection<String> claimTokens);

  @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true)
  @Query(
      "UPDATE LinkEntity l SET l.viewCount = l.viewCount + 1 "
          + "WHERE l.id = :linkId AND (l.maxViews IS NULL OR l.viewCount < l.maxViews)")
  int incrementViewCountIfBelowLimit(@Param("linkId") Long linkId);

  @org.springframework.data.jpa.repository.Modifying(
      clearAutomatically = true,
      flushAutomatically = true)
  @org.springframework.data.jpa.repository.Query(
      "DELETE FROM LinkEntity l WHERE l.userId = :userId")
  int deleteByUserId(@org.springframework.data.repository.query.Param("userId") Long userId);
}
