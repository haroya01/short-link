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

  /** {@code q}는 소문자 {@code %pattern%}이며, null이면 검색하지 않는다. */
  @Query(
      value =
          "SELECT u.id AS id, u.email AS email, u.username AS username, "
              + "u.role AS role, u.createdAt AS createdAt, "
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
          + "u.role AS role, u.createdAt AS createdAt, "
          + "u.deletedAt AS deletedAt, "
          + "(SELECT COUNT(l) FROM LinkEntity l WHERE l.userId = u.id) AS linkCount "
          + "FROM UserEntity u WHERE u.id = :id")
  Optional<UserRow> findUserRowById(@Param("id") long id);

  /** ShortCode의 AttributeConverter 때문에 LIKE 대신 일치 비교를 쓴다. null 필터는 적용하지 않는다. */
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

  /** JPQL의 SELECT 별칭 정렬 지원에 의존하지 않도록 ORDER BY에 집계식을 반복한다. */
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
