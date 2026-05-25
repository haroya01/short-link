package com.example.short_link.link.domain.repository;

import com.example.short_link.link.domain.*;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LinkRepository
    extends JpaRepository<LinkEntity, Long>, JpaSpecificationExecutor<LinkEntity> {
  Optional<LinkEntity> findByShortCode(String shortCode);

  List<LinkEntity> findAllByUserIdOrderByCreatedAtDesc(Long userId);

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

  List<LinkEntity> findAllByUserIdAndProfileOrderIsNotNullOrderByProfileOrderAsc(Long userId);

  List<LinkEntity> findAllByUserIdAndProfileHighlightedIsTrue(Long userId);

  Optional<LinkEntity> findFirstByUserIdAndOriginalUrl(Long userId, String originalUrl);

  List<LinkEntity> findAllByClaimTokenInAndUserIdIsNull(Collection<String> claimTokens);

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
