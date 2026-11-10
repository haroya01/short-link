package com.example.short_link.link.infrastructure.persistence;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository.CachedLinkRow;
import com.example.short_link.link.domain.repository.LinkRepository.ProfileLinkRow;
import com.example.short_link.link.domain.repository.LinkRepository.SafetyRescanRow;
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
import org.springframework.transaction.annotation.Transactional;

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
        l.blockedCountries AS blockedCountries,
        CASE WHEN l.passwordHash IS NULL THEN false ELSE true END AS passwordRequired,
        l.maxViews AS maxViews,
        l.expiredMessage AS expiredMessage,
        visit.openInBrowser AS openInBrowser,
        visit.splashEnabled AS splashEnabled,
        visit.splashMessage AS splashMessage,
        visit.splashSeconds AS splashSeconds,
        visit.splashCtaId AS splashCtaId,
        visit.opensAt AS opensAt,
        CASE WHEN moderation.linkId IS NULL THEN false ELSE true END AS disabled,
        l.statsPublic AS statsPublic
      FROM LinkEntity l
      LEFT JOIN LinkVisitOptionEntity visit ON visit.linkId = l.id
      LEFT JOIN LinkModerationEntity moderation ON moderation.linkId = l.id
      WHERE l.shortCode = :shortCode
      """)
  Optional<CachedLinkRow> findCachedLinkRowByShortCode(@Param("shortCode") ShortCode shortCode);

  @Query(
      """
      SELECT l.id AS linkId, l.originalUrl AS originalUrl
      FROM LinkEntity l
      LEFT JOIN LinkModerationEntity moderation ON moderation.linkId = l.id
      WHERE l.id > :afterId
        AND moderation.linkId IS NULL
        AND (l.expiresAt IS NULL OR l.expiresAt > :now)
      ORDER BY l.id ASC
      """)
  List<SafetyRescanRow> findSafetyRescanBatch(
      @Param("afterId") Long afterId, @Param("now") Instant now, Pageable page);

  List<LinkEntity> findAllByUserIdAndFavoriteOrderIsNotNullOrderByFavoriteOrderAscIdAsc(
      Long userId);

  List<LinkEntity> findAllByUserIdOrderByCreatedAtDesc(Long userId);

  List<LinkEntity> findTop500ByExpiresAtBeforeOrderByExpiresAtAsc(Instant when);

  @Query("select distinct l.userId from LinkEntity l")
  List<Long> findDistinctUserIds();

  List<LinkEntity> findByOriginalUrlContaining(String fragment);

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

  @Query(
      """
      SELECT
        l AS link,
        visit.opensAt AS opensAt,
        CASE WHEN l.passwordHash IS NULL THEN false ELSE true END AS passwordRequired
      FROM LinkEntity l
      LEFT JOIN LinkVisitOptionEntity visit ON visit.linkId = l.id
      WHERE l.userId = :userId
        AND l.profileOrder IS NOT NULL
        AND NOT EXISTS (
          SELECT 1 FROM LinkModerationEntity moderation WHERE moderation.linkId = l.id)
      ORDER BY l.profileOrder ASC
      """)
  List<ProfileLinkRow> findPublicProfileLinks(@Param("userId") Long userId);

  @Query(
      """
      SELECT l.shortCode AS shortCode FROM LinkEntity l
      WHERE l.shortCode IN :shortCodes
        AND l.maxViews IS NOT NULL
        AND l.viewCount >= l.maxViews
      """)
  List<ShortCodeRow> findShortCodesWithViewLimitReached(
      @Param("shortCodes") Collection<ShortCode> shortCodes);

  interface ShortCodeRow {
    ShortCode getShortCode();
  }

  @Query(
      """
      SELECT l FROM LinkEntity l
      LEFT JOIN LinkVisitOptionEntity visit ON visit.linkId = l.id
      WHERE l.userId = :userId
        AND l.originalUrl = :originalUrl
        AND l.expiresAt IS NULL
        AND l.passwordHash IS NULL
        AND l.maxViews IS NULL
        AND l.blockedCountries IS NULL
        AND visit.opensAt IS NULL
        AND NOT EXISTS (
          SELECT 1 FROM LinkModerationEntity moderation WHERE moderation.linkId = l.id)
        AND NOT EXISTS (
          SELECT 1 FROM LinkDestinationEntity d WHERE d.linkId = l.id AND d.enabled = true)
      ORDER BY l.id ASC
      """)
  List<LinkEntity> findUnrestrictedByUserIdAndOriginalUrl(
      @Param("userId") Long userId, @Param("originalUrl") String originalUrl);

  List<LinkEntity> findAllByClaimTokenInAndUserIdIsNull(Collection<String> claimTokens);

  @Modifying(clearAutomatically = true)
  @Query(
      "UPDATE LinkEntity l SET l.viewCount = l.viewCount + 1 "
          + "WHERE l.id = :linkId AND (l.maxViews IS NULL OR l.viewCount < l.maxViews)")
  int incrementViewCountIfBelowLimit(@Param("linkId") Long linkId);

  @Transactional
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE LinkEntity l SET l.ogTitle = :title, l.ogDescription = :description, "
          + "l.ogImage = :image, l.ogFetchedAt = :fetchedAt, l.ogFetchStatus = :status, "
          + "l.ogFetchAttempts = l.ogFetchAttempts + 1 WHERE l.id = :linkId")
  int recordOgFetched(
      @Param("linkId") Long linkId,
      @Param("title") String title,
      @Param("description") String description,
      @Param("image") String image,
      @Param("fetchedAt") Instant fetchedAt,
      @Param("status") String status);

  @Modifying(flushAutomatically = true)
  @Query(
      "UPDATE LinkEntity l SET l.ogTitle = NULL, l.ogDescription = NULL, l.ogImage = NULL, "
          + "l.ogFetchedAt = NULL, l.ogFetchStatus = :pending WHERE l.id = :linkId")
  int restartOgFetch(@Param("linkId") Long linkId, @Param("pending") String pending);

  @Transactional
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE LinkEntity l SET l.ogFetchedAt = :fetchedAt, l.ogFetchStatus = :status, "
          + "l.ogFetchAttempts = l.ogFetchAttempts + 1 WHERE l.id = :linkId")
  int recordOgFetchFailed(
      @Param("linkId") Long linkId,
      @Param("fetchedAt") Instant fetchedAt,
      @Param("status") String status);

  @Transactional
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE LinkEntity l SET l.ogFetchStatus = :retry, l.ogFetchAttempts = 0 "
          + "WHERE l.ogFetchStatus = :gaveUp")
  int reopenOgFetches(@Param("gaveUp") String gaveUp, @Param("retry") String retry);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("DELETE FROM LinkEntity l WHERE l.userId = :userId")
  int deleteByUserId(@Param("userId") Long userId);
}
