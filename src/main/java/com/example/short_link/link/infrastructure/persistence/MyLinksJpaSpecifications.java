package com.example.short_link.link.infrastructure.persistence;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkExpiryFilter;
import com.example.short_link.link.domain.repository.MyLinksSearchCriteria;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import org.springframework.data.jpa.domain.Specification;

final class MyLinksJpaSpecifications {
  private static final Duration EXPIRING_SOON_WINDOW = Duration.ofDays(3);

  private MyLinksJpaSpecifications() {}

  static Specification<LinkEntity> from(MyLinksSearchCriteria criteria) {
    Instant now = Instant.now();
    Specification<LinkEntity> spec = ownedBy(criteria.userId());
    spec = and(spec, matchesQuery(criteria.text()));
    spec = and(spec, idIn(criteria.linkIds()));
    spec = and(spec, domainContains(criteria.domain()));
    spec = and(spec, expiry(criteria.expiry(), now));
    spec = and(spec, createdAfter(criteria.createdAfter()));
    spec = and(spec, createdBefore(criteria.createdBefore()));
    return spec;
  }

  static Specification<LinkEntity> cursorAfter(
      Instant cursorCreatedAt, Long cursorId, boolean ascending) {
    if (cursorCreatedAt == null || cursorId == null) return null;
    if (ascending) {
      return (root, query, cb) ->
          cb.or(
              cb.greaterThan(root.get("createdAt"), cursorCreatedAt),
              cb.and(
                  cb.equal(root.get("createdAt"), cursorCreatedAt),
                  cb.greaterThan(root.get("id"), cursorId)));
    }
    return (root, query, cb) ->
        cb.or(
            cb.lessThan(root.get("createdAt"), cursorCreatedAt),
            cb.and(
                cb.equal(root.get("createdAt"), cursorCreatedAt),
                cb.lessThan(root.get("id"), cursorId)));
  }

  private static Specification<LinkEntity> ownedBy(Long userId) {
    return (root, query, cb) -> cb.equal(root.get("userId"), userId);
  }

  private static Specification<LinkEntity> matchesQuery(String text) {
    if (text == null || text.isBlank()) return null;
    return (root, query, cb) -> {
      String like = "%" + text.toLowerCase() + "%";
      return cb.or(
          cb.like(cb.lower(root.get("originalUrl").as(String.class)), like),
          cb.like(cb.lower(root.get("shortCode")), like));
    };
  }

  private static Specification<LinkEntity> idIn(Collection<Long> ids) {
    if (ids == null) return null;
    if (ids.isEmpty()) return (root, query, cb) -> cb.disjunction();
    return (root, query, cb) -> root.get("id").in(ids);
  }

  private static Specification<LinkEntity> domainContains(String domain) {
    if (domain == null || domain.isBlank()) return null;
    String like = "%" + domain.trim().toLowerCase() + "%";
    return (root, query, cb) -> cb.like(cb.lower(root.get("originalUrl").as(String.class)), like);
  }

  private static Specification<LinkEntity> expiry(LinkExpiryFilter filter, Instant now) {
    if (filter == null || filter == LinkExpiryFilter.ALL) return null;
    return switch (filter) {
      case NEVER -> (root, query, cb) -> cb.isNull(root.get("expiresAt"));
      case HAS_EXPIRY -> (root, query, cb) -> cb.isNotNull(root.get("expiresAt"));
      case EXPIRED ->
          (root, query, cb) ->
              cb.and(cb.isNotNull(root.get("expiresAt")), cb.lessThan(root.get("expiresAt"), now));
      case ACTIVE ->
          (root, query, cb) ->
              cb.or(
                  cb.isNull(root.get("expiresAt")),
                  cb.greaterThanOrEqualTo(root.get("expiresAt"), now));
      case EXPIRING_SOON ->
          (root, query, cb) ->
              cb.and(
                  cb.isNotNull(root.get("expiresAt")),
                  cb.greaterThanOrEqualTo(root.get("expiresAt"), now),
                  cb.lessThan(root.get("expiresAt"), now.plus(EXPIRING_SOON_WINDOW)));
      case ALL -> null;
    };
  }

  private static Specification<LinkEntity> createdAfter(Instant after) {
    if (after == null) return null;
    return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), after);
  }

  private static Specification<LinkEntity> createdBefore(Instant before) {
    if (before == null) return null;
    return (root, query, cb) -> cb.lessThan(root.get("createdAt"), before);
  }

  private static Specification<LinkEntity> and(
      Specification<LinkEntity> base, Specification<LinkEntity> piece) {
    return piece == null ? base : base.and(piece);
  }
}
