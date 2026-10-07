package com.example.short_link.user.infrastructure.persistence;

import com.example.short_link.user.domain.repository.FollowSuggestionReader;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Repository;

// One statement: friends' picks ranked by how many of the member's follows follow them, then
// accounts much followed among those who posted a note or published a post lately. Anyone the
// member already follows or asked to follow, blocked either way, muted, or set aside never shows.
@Repository
class FollowSuggestionReaderAdapter implements FollowSuggestionReader {

  static final int POPULAR_POOL = 50;

  @PersistenceContext private EntityManager em;

  @Override
  @SuppressWarnings("unchecked")
  public List<Suggestion> suggestions(Long userId, Instant activeSince, int limit) {
    List<Object[]> rows =
        em.createNativeQuery(
                "SELECT u.username, u.display_name, u.avatar_url, u.bio, s.mutuals, s.reason, u.locked"
                    + " FROM (SELECT c.uid, MAX(c.mutuals) AS mutuals, MIN(c.reason) AS reason"
                    + " FROM (SELECT f2.following_id AS uid, COUNT(*) AS mutuals, 0 AS reason"
                    + " FROM user_follow f1 JOIN user_follow f2 ON f2.follower_id = f1.following_id"
                    + " WHERE f1.follower_id = :me GROUP BY f2.following_id"
                    + " UNION ALL (SELECT p.following_id, 0, 1 FROM user_follow p"
                    + " WHERE EXISTS (SELECT 1 FROM note n WHERE n.user_id = p.following_id"
                    + " AND n.created_at > :since)"
                    + " OR EXISTS (SELECT 1 FROM posts po WHERE po.user_id = p.following_id"
                    + " AND po.status = 'PUBLISHED' AND po.published_at > :since)"
                    + " GROUP BY p.following_id ORDER BY COUNT(*) DESC LIMIT "
                    + POPULAR_POOL
                    + ")) c GROUP BY c.uid) s"
                    + " JOIN users u ON u.id = s.uid"
                    + " WHERE u.id <> :me AND u.deleted_at IS NULL AND u.username IS NOT NULL"
                    + " AND NOT EXISTS (SELECT 1 FROM user_follow mf"
                    + " WHERE mf.follower_id = :me AND mf.following_id = u.id)"
                    + " AND NOT EXISTS (SELECT 1 FROM follow_request fr"
                    + " WHERE fr.follower_id = :me AND fr.following_id = u.id)"
                    + " AND NOT EXISTS (SELECT 1 FROM user_block b WHERE (b.blocker_id = :me"
                    + " AND b.blocked_id = u.id) OR (b.blocker_id = u.id AND b.blocked_id = :me))"
                    + " AND NOT EXISTS (SELECT 1 FROM user_mute m WHERE m.user_id = :me"
                    + " AND m.muted_user_id = u.id)"
                    + " AND NOT EXISTS (SELECT 1 FROM follow_suggestion_dismissal d"
                    + " WHERE d.user_id = :me AND d.dismissed_id = u.id)"
                    + " ORDER BY s.reason, s.mutuals DESC, u.id DESC LIMIT :limit")
            .setParameter("me", userId)
            .setParameter("since", activeSince)
            .setParameter("limit", limit)
            .getResultList();
    return rows.stream()
        .map(
            row ->
                new Suggestion(
                    row[0].toString(),
                    row[1] == null ? null : row[1].toString(),
                    row[2] == null ? null : row[2].toString(),
                    row[3] == null ? null : row[3].toString(),
                    ((Number) row[4]).longValue(),
                    ((Number) row[5]).intValue() == 0 ? Reason.FRIENDS : Reason.POPULAR,
                    truth(row[6])))
        .toList();
  }

  private static boolean truth(Object value) {
    return switch (value) {
      case null -> false;
      case Boolean b -> b;
      case Number n -> n.intValue() != 0;
      default -> Boolean.parseBoolean(value.toString());
    };
  }

  @Override
  public void dismiss(Long userId, Long dismissedId) {
    em.createNativeQuery(
            "INSERT IGNORE INTO follow_suggestion_dismissal (user_id, dismissed_id, created_at)"
                + " VALUES (:me, :dismissed, :now)")
        .setParameter("me", userId)
        .setParameter("dismissed", dismissedId)
        .setParameter("now", Instant.now())
        .executeUpdate();
  }
}
