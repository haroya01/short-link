package com.example.short_link.link.infrastructure.persistence;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository.CachedLinkRow;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaLinkRepository
    extends JpaRepository<LinkEntity, Long>, JpaSpecificationExecutor<LinkEntity> {

  Optional<LinkEntity> findByShortCode(ShortCode shortCode);

  List<LinkEntity> findAllByShortCodeInAndUserId(Collection<ShortCode> shortCodes, Long userId);

  @Query(
      """
      SELECT
        l.id AS id,
        l.shortCode AS shortCode,
        l.userId AS userId,
        l.originalUrl AS originalUrl,
        l.expiresAt AS expiresAt,
        COALESCE(og.ogTitle, l.ogTitle) AS ogTitle,
        COALESCE(og.ogDescription, l.ogDescription) AS ogDescription,
        COALESCE(og.ogImage, l.ogImage) AS ogImage,
        COALESCE(policy.blockedCountries, l.blockedCountries) AS blockedCountries,
        CASE
          WHEN COALESCE(acl.passwordHash, l.passwordHash) IS NULL THEN false
          ELSE true
        END AS passwordRequired,
        COALESCE(acl.maxViews, l.maxViews) AS maxViews,
        COALESCE(policy.expiredMessage, l.expiredMessage) AS expiredMessage
      FROM LinkEntity l
      LEFT JOIN LinkOgMetadataEntity og ON og.linkId = l.id
      LEFT JOIN LinkAccessControlEntity acl ON acl.linkId = l.id
      LEFT JOIN LinkExpirationPolicyEntity policy ON policy.linkId = l.id
      WHERE l.shortCode = :shortCode
      """)
  Optional<CachedLinkRow> findCachedLinkRowByShortCode(@Param("shortCode") ShortCode shortCode);

  List<LinkEntity> findAllByUserIdOrderByCreatedAtDesc(Long userId);

  long countByCreatedAtAfter(Instant since);

  long countByUserIdIsNull();

  long countByExpiresAtBefore(Instant when);

  List<LinkEntity> findTop500ByExpiresAtBeforeOrderByExpiresAtAsc(Instant when);

  @Query("select distinct l.userId from LinkEntity l")
  List<Long> findDistinctUserIds();

  List<LinkEntity> findByExpiresAtBetween(Instant from, Instant to);

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

  @Modifying(clearAutomatically = true)
  @Query(
      "UPDATE LinkEntity l SET l.viewCount = l.viewCount + 1 "
          + "WHERE l.id = :linkId AND (l.maxViews IS NULL OR l.viewCount < l.maxViews)")
  int incrementViewCountIfBelowLimit(@Param("linkId") Long linkId);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("DELETE FROM LinkEntity l WHERE l.userId = :userId")
  int deleteByUserId(@Param("userId") Long userId);
}
