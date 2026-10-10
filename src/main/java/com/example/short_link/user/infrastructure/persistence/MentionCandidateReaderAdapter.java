package com.example.short_link.user.infrastructure.persistence;

import com.example.short_link.user.domain.repository.MentionCandidateReader;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
class MentionCandidateReaderAdapter implements MentionCandidateReader {

  private static final String VISIBLE =
      " u.id <> :me AND u.deleted_at IS NULL AND u.username IS NOT NULL"
          + " AND NOT EXISTS (SELECT 1 FROM user_block b WHERE (b.blocker_id = :me"
          + " AND b.blocked_id = u.id) OR (b.blocker_id = u.id AND b.blocked_id = :me))";

  @PersistenceContext private EntityManager em;

  @Override
  @SuppressWarnings("unchecked")
  public List<Candidate> followed(Long userId, int limit) {
    List<Object[]> rows =
        em.createNativeQuery(
                "SELECT u.username, u.display_name, u.avatar_url, 1, u.id FROM user_follow f"
                    + " JOIN users u ON u.id = f.following_id WHERE f.follower_id = :me AND"
                    + VISIBLE
                    + " ORDER BY f.created_at DESC, f.id DESC LIMIT :limit")
            .setParameter("me", userId)
            .setParameter("limit", limit)
            .getResultList();
    return rows.stream().map(MentionCandidateReaderAdapter::candidate).toList();
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<Candidate> matching(Long userId, String prefix, int limit) {
    String like = escape(prefix) + "%";
    List<Object[]> rows =
        em.createNativeQuery(
                "SELECT u.username, u.display_name, u.avatar_url, (f.id IS NOT NULL), u.id FROM users u"
                    + " LEFT JOIN user_follow f ON f.follower_id = :me AND f.following_id = u.id"
                    + " WHERE (u.username LIKE :like OR u.display_name LIKE :like) AND"
                    + VISIBLE
                    + " ORDER BY (f.id IS NOT NULL) DESC, (u.username = :exact) DESC,"
                    + " CHAR_LENGTH(u.username), u.username LIMIT :limit")
            .setParameter("me", userId)
            .setParameter("like", like)
            .setParameter("exact", prefix)
            .setParameter("limit", limit)
            .getResultList();
    return rows.stream().map(MentionCandidateReaderAdapter::candidate).toList();
  }

  private static String escape(String text) {
    return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }

  private static Candidate candidate(Object[] row) {
    return new Candidate(
        ((Number) row[4]).longValue(),
        row[0].toString(),
        row[1] == null ? null : row[1].toString(),
        row[2] == null ? null : row[2].toString(),
        row[3] instanceof Number n ? n.intValue() != 0 : Boolean.TRUE.equals(row[3]));
  }
}
