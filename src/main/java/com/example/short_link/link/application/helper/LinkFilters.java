package com.example.short_link.link.application.helper;

import com.example.short_link.link.application.dto.MyLinksCursor;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.tag.domain.LinkTagEntity;
import com.example.short_link.tag.domain.TagEntity;
import jakarta.persistence.criteria.Subquery;
import java.time.Duration;
import java.time.Instant;
import org.springframework.data.jpa.domain.Specification;

/**
 * Composable {@link Specification} pieces that drive the my-links filter UI. Each piece is
 * independent — null/blank inputs return {@code null} so that {@code Specification.where(...)}
 * skips them. Combining {@code .and(...)} on null yields {@code null} so blank filters never narrow
 * the result set.
 *
 * <p>Click count is intentionally not modeled here — it lives on the {@code click_event} table and
 * would require a denormalized counter or aggregating join; we surface it as a separate API concern
 * when needed.
 */
public final class LinkFilters {

  public enum ExpiryFilter {
    ALL,
    NEVER,
    ACTIVE,
    EXPIRED,
    HAS_EXPIRY,
    /**
     * Links whose expiresAt falls in [now, now + EXPIRING_SOON_WINDOW). Drives the "이거 곧 만료" 배너.
     */
    EXPIRING_SOON
  }

  /**
   * How far ahead "expiring soon" looks. Tuned so the dashboard banner gives ~3 days of warning.
   */
  public static final Duration EXPIRING_SOON_WINDOW = Duration.ofDays(3);

  private LinkFilters() {}

  public static Specification<LinkEntity> ownedBy(Long userId) {
    return (root, query, cb) -> cb.equal(root.get("userId"), userId);
  }

  public static Specification<LinkEntity> matchesQuery(String q) {
    if (q == null || q.isBlank()) return null;
    return (root, query, cb) -> {
      String like = "%" + q.toLowerCase() + "%";
      return cb.or(
          cb.like(cb.lower(root.get("originalUrl").as(String.class)), like),
          cb.like(cb.lower(root.get("shortCode")), like));
    };
  }

  public static Specification<LinkEntity> domainContains(String domain) {
    if (domain == null || domain.isBlank()) return null;
    String like = "%" + domain.trim().toLowerCase() + "%";
    return (root, query, cb) -> cb.like(cb.lower(root.get("originalUrl").as(String.class)), like);
  }

  public static Specification<LinkEntity> expiry(ExpiryFilter filter, Instant now) {
    if (filter == null || filter == ExpiryFilter.ALL) return null;
    return switch (filter) {
      case NEVER -> (root, q, cb) -> cb.isNull(root.get("expiresAt"));
      case HAS_EXPIRY -> (root, q, cb) -> cb.isNotNull(root.get("expiresAt"));
      case EXPIRED ->
          (root, q, cb) ->
              cb.and(cb.isNotNull(root.get("expiresAt")), cb.lessThan(root.get("expiresAt"), now));
      case ACTIVE ->
          (root, q, cb) ->
              cb.or(
                  cb.isNull(root.get("expiresAt")),
                  cb.greaterThanOrEqualTo(root.get("expiresAt"), now));
      case EXPIRING_SOON ->
          (root, q, cb) ->
              cb.and(
                  cb.isNotNull(root.get("expiresAt")),
                  cb.greaterThanOrEqualTo(root.get("expiresAt"), now),
                  cb.lessThan(root.get("expiresAt"), now.plus(EXPIRING_SOON_WINDOW)));
      case ALL -> null;
    };
  }

  public static Specification<LinkEntity> createdAfter(Instant after) {
    if (after == null) return null;
    return (root, q, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), after);
  }

  public static Specification<LinkEntity> createdBefore(Instant before) {
    if (before == null) return null;
    return (root, q, cb) -> cb.lessThan(root.get("createdAt"), before);
  }

  /**
   * Cursor predicate for descending (createdAt, id) pagination: row qualifies iff its createdAt is
   * strictly older than the cursor, OR equal createdAt with strictly smaller id. The id tie-break
   * is what stops same-millisecond rows from being skipped or duplicated across pages.
   */
  public static Specification<LinkEntity> cursorAfter(MyLinksCursor cursor) {
    if (cursor == null) return null;
    return (root, q, cb) ->
        cb.or(
            cb.lessThan(root.get("createdAt"), cursor.createdAt()),
            cb.and(
                cb.equal(root.get("createdAt"), cursor.createdAt()),
                cb.lessThan(root.get("id"), cursor.id())));
  }

  /**
   * "has a tag with this exact name owned by the same user" — implemented as an IN-subquery against
   * the {@code link_tag} / {@code tag} join so we don't double-count rows from a join fetch.
   */
  public static Specification<LinkEntity> hasTagName(Long userId, String tagName) {
    if (tagName == null || tagName.isBlank()) return null;
    return (root, query, cb) -> {
      Subquery<Long> sub = query.subquery(Long.class);
      var lt = sub.from(LinkTagEntity.class);
      var tagJoin = sub.from(TagEntity.class);
      sub.select(lt.get("linkId"))
          .where(
              cb.equal(lt.get("tagId"), tagJoin.get("id")),
              cb.equal(tagJoin.get("userId"), userId),
              cb.equal(tagJoin.get("name"), tagName.trim()));
      return root.get("id").in(sub);
    };
  }
}
