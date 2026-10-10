package com.example.short_link.user.infrastructure.persistence;

import com.example.short_link.common.user.HeardSql;
import com.example.short_link.user.domain.repository.UserSearchReader;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
class UserSearchReaderAdapter implements UserSearchReader {

  private static final String SEARCH =
      "SELECT u.username, u.display_name, u.avatar_url, u.bio, u.hide_follower_count,"
          + " (SELECT COUNT(*) FROM user_follow c JOIN users cu ON cu.id = c.follower_id"
          + " WHERE c.following_id = u.id AND cu.deleted_at IS NULL"
          + " AND cu.username IS NOT NULL) AS followers,"
          + " (f.id IS NOT NULL), (r.id IS NOT NULL) FROM users u"
          + " LEFT JOIN user_follow f ON f.follower_id = :viewer AND f.following_id = u.id"
          + " LEFT JOIN follow_request r ON r.follower_id = :viewer AND r.following_id = u.id"
          + " WHERE (u.username LIKE :prefix OR u.display_name LIKE :contains)"
          + " AND u.deleted_at IS NULL AND u.username IS NOT NULL"
          + " AND u.moderation_status <> 'BANNED'"
          + " AND NOT (u.moderation_status = 'SUSPENDED' AND u.suspended_until > :now)"
          + HeardSql.unblocked("u.id")
          + " ORDER BY (u.username = :exact) DESC, (f.id IS NOT NULL) DESC,"
          + " (u.username LIKE :prefix) DESC,"
          + " followers DESC, u.username";

  @PersistenceContext private EntityManager em;

  @Override
  @SuppressWarnings("unchecked")
  public List<Match> search(Long viewerId, String query, Instant now, int offset, int limit) {
    String escaped = escape(query);
    List<Object[]> rows =
        em.createNativeQuery(SEARCH)
            .setParameter("viewer", HeardSql.viewer(viewerId))
            .setParameter("prefix", escaped + "%")
            .setParameter("contains", "%" + escaped + "%")
            .setParameter("exact", query)
            .setParameter("now", now)
            .setFirstResult(offset)
            .setMaxResults(limit)
            .getResultList();
    return rows.stream().map(UserSearchReaderAdapter::match).toList();
  }

  private static String escape(String text) {
    return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }

  private static Match match(Object[] row) {
    return new Match(
        row[0].toString(),
        text(row[1]),
        text(row[2]),
        text(row[3]),
        ((Number) row[5]).longValue(),
        flag(row[4]),
        flag(row[6]),
        flag(row[7]));
  }

  private static String text(Object value) {
    return value == null ? null : value.toString();
  }

  private static boolean flag(Object value) {
    return value instanceof Number n ? n.intValue() != 0 : Boolean.TRUE.equals(value);
  }
}
