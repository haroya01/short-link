package com.example.short_link.admin.infrastructure.persistence;

import com.example.short_link.admin.domain.repository.AdminBrowseRepository.LinkRow;
import com.example.short_link.admin.domain.repository.AdminBrowseRepository.UserRow;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.user.domain.UserEntity;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaAdminBrowseRepository extends JpaRepository<UserEntity, Long> {

  /**
   * {@code q} is a pre-lowercased {@code %pattern%} (or null for no filter); email/username are
   * matched case-insensitively. {@code linkCount} is a correlated subquery — cheap enough for a
   * page of rows and keeps anonymous links (which have no owner) out of the tally.
   */
  @Query(
      value =
          "SELECT u.id AS id, u.email AS email, u.username AS username, "
              + "u.role AS role, u.tier AS tier, u.createdAt AS createdAt, "
              + "u.deletedAt AS deletedAt, "
              + "(SELECT COUNT(l) FROM LinkEntity l WHERE l.userId = u.id) AS linkCount "
              + "FROM UserEntity u "
              + "WHERE (:q IS NULL OR LOWER(u.email) LIKE :q OR LOWER(u.username) LIKE :q) "
              + "AND (:role IS NULL OR u.role = :role) "
              + "ORDER BY u.createdAt DESC",
      countQuery =
          "SELECT COUNT(u) FROM UserEntity u "
              + "WHERE (:q IS NULL OR LOWER(u.email) LIKE :q OR LOWER(u.username) LIKE :q) "
              + "AND (:role IS NULL OR u.role = :role)")
  Page<UserRow> findUsers(
      @Param("q") String q, @Param("role") UserEntity.Role role, Pageable pageable);

  @Query(
      "SELECT u.id AS id, u.email AS email, u.username AS username, "
          + "u.role AS role, u.tier AS tier, u.createdAt AS createdAt, "
          + "u.deletedAt AS deletedAt, "
          + "(SELECT COUNT(l) FROM LinkEntity l WHERE l.userId = u.id) AS linkCount "
          + "FROM UserEntity u WHERE u.id = :id")
  Optional<UserRow> findUserRowById(@Param("id") long id);

  /**
   * The short code column is backed by an {@code AttributeConverter}, so it can't take a {@code
   * LIKE} — {@code exactCode} matches a code verbatim while {@code urlPattern} substring-matches
   * the destination. Either being null drops that clause; both null means "no filter".
   */
  @Query(
      value =
          "SELECT l.shortCode AS shortCode, l.originalUrl AS originalUrl, "
              + "l.userId AS ownerId, u.email AS ownerEmail, "
              + "(SELECT COUNT(c) FROM ClickEventEntity c WHERE c.linkId = l.id) AS clickCount, "
              + "l.createdAt AS createdAt, l.expiresAt AS expiresAt, "
              + "l.maxViews AS maxViews, l.viewCount AS viewCount, "
              + "CASE WHEN l.passwordHash IS NOT NULL THEN 1 ELSE 0 END AS passwordProtected "
              + "FROM LinkEntity l LEFT JOIN UserEntity u ON u.id = l.userId "
              + "WHERE (:ownerId IS NULL OR l.userId = :ownerId) "
              + "AND (:urlPattern IS NULL OR LOWER(l.originalUrl) LIKE :urlPattern "
              + "OR (:exactCode IS NOT NULL AND l.shortCode = :exactCode)) "
              + "ORDER BY l.createdAt DESC",
      countQuery =
          "SELECT COUNT(l) FROM LinkEntity l "
              + "WHERE (:ownerId IS NULL OR l.userId = :ownerId) "
              + "AND (:urlPattern IS NULL OR LOWER(l.originalUrl) LIKE :urlPattern "
              + "OR (:exactCode IS NOT NULL AND l.shortCode = :exactCode))")
  Page<LinkRow> findLinks(
      @Param("urlPattern") String urlPattern,
      @Param("exactCode") ShortCode exactCode,
      @Param("ownerId") Long ownerId,
      Pageable pageable);

  /**
   * Same projection and filters as {@link #findLinks}, ordered by lifetime click count (busiest
   * first) with newest as the tie-break. Ordering repeats the correlated count expression rather
   * than the select alias so it doesn't depend on alias-in-ORDER-BY support.
   */
  @Query(
      value =
          "SELECT l.shortCode AS shortCode, l.originalUrl AS originalUrl, "
              + "l.userId AS ownerId, u.email AS ownerEmail, "
              + "(SELECT COUNT(c) FROM ClickEventEntity c WHERE c.linkId = l.id) AS clickCount, "
              + "l.createdAt AS createdAt, l.expiresAt AS expiresAt, "
              + "l.maxViews AS maxViews, l.viewCount AS viewCount, "
              + "CASE WHEN l.passwordHash IS NOT NULL THEN 1 ELSE 0 END AS passwordProtected "
              + "FROM LinkEntity l LEFT JOIN UserEntity u ON u.id = l.userId "
              + "WHERE (:ownerId IS NULL OR l.userId = :ownerId) "
              + "AND (:urlPattern IS NULL OR LOWER(l.originalUrl) LIKE :urlPattern "
              + "OR (:exactCode IS NOT NULL AND l.shortCode = :exactCode)) "
              + "ORDER BY (SELECT COUNT(c) FROM ClickEventEntity c WHERE c.linkId = l.id) DESC, "
              + "l.createdAt DESC",
      countQuery =
          "SELECT COUNT(l) FROM LinkEntity l "
              + "WHERE (:ownerId IS NULL OR l.userId = :ownerId) "
              + "AND (:urlPattern IS NULL OR LOWER(l.originalUrl) LIKE :urlPattern "
              + "OR (:exactCode IS NOT NULL AND l.shortCode = :exactCode))")
  Page<LinkRow> findLinksByClicks(
      @Param("urlPattern") String urlPattern,
      @Param("exactCode") ShortCode exactCode,
      @Param("ownerId") Long ownerId,
      Pageable pageable);

  /** Single link by exact code, same projection as the browse list — for the admin detail view. */
  @Query(
      "SELECT l.shortCode AS shortCode, l.originalUrl AS originalUrl, "
          + "l.userId AS ownerId, u.email AS ownerEmail, "
          + "(SELECT COUNT(c) FROM ClickEventEntity c WHERE c.linkId = l.id) AS clickCount, "
          + "l.createdAt AS createdAt, l.expiresAt AS expiresAt, "
          + "l.maxViews AS maxViews, l.viewCount AS viewCount, "
          + "CASE WHEN l.passwordHash IS NOT NULL THEN 1 ELSE 0 END AS passwordProtected "
          + "FROM LinkEntity l LEFT JOIN UserEntity u ON u.id = l.userId "
          + "WHERE l.shortCode = :shortCode")
  Optional<LinkRow> findLinkByShortCode(@Param("shortCode") ShortCode shortCode);
}
