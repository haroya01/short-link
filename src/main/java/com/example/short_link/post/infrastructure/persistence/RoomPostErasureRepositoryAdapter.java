package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.post.domain.repository.RoomPostErasureRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
class RoomPostErasureRepositoryAdapter implements RoomPostErasureRepository {

  @PersistenceContext private EntityManager em;

  @Override
  public void request(Long userId, Long roomId, Instant at) {
    em.createNativeQuery(
            "INSERT IGNORE INTO room_post_erasure (user_id, room_id, requested_at)"
                + " VALUES (:user, :room, :at)")
        .setParameter("user", userId)
        .setParameter("room", roomId)
        .setParameter("at", at)
        .executeUpdate();
  }

  @Override
  public List<Request> pending(int limit) {
    List<Request> requests = new ArrayList<>();
    for (Object raw :
        em.createNativeQuery(
                "SELECT user_id, room_id FROM room_post_erasure ORDER BY requested_at, user_id")
            .setMaxResults(limit)
            .getResultList()) {
      Object[] row = (Object[]) raw;
      requests.add(new Request(((Number) row[0]).longValue(), ((Number) row[1]).longValue()));
    }
    return requests;
  }

  @Override
  public void finish(Long userId, Long roomId) {
    em.createNativeQuery("DELETE FROM room_post_erasure WHERE user_id = :user AND room_id = :room")
        .setParameter("user", userId)
        .setParameter("room", roomId)
        .executeUpdate();
  }

  @Override
  public List<Long> postIdsBy(Long userId, Long roomId, int limit) {
    return ids(
        "SELECT id FROM posts WHERE user_id = :user AND room_id = :room ORDER BY id",
        userId,
        roomId,
        limit);
  }

  @Override
  public List<Long> commentIdsBy(Long userId, Long roomId, int limit) {
    return ids(
        "SELECT id FROM comment WHERE user_id = :user AND room_id = :room"
            + " AND deleted_at IS NULL ORDER BY id",
        userId,
        roomId,
        limit);
  }

  @Override
  public List<Long> highlightIdsBy(Long userId, Long roomId, int limit) {
    return ids(
        "SELECT id FROM post_highlight WHERE user_id = :user AND room_id = :room ORDER BY id",
        userId,
        roomId,
        limit);
  }

  @Override
  public List<Long> highlightReplyIdsBy(Long userId, Long roomId, int limit) {
    return ids(
        "SELECT r.id FROM highlight_reply r JOIN post_highlight h ON h.id = r.highlight_id"
            + " WHERE r.user_id = :user AND h.room_id = :room ORDER BY r.id",
        userId,
        roomId,
        limit);
  }

  private List<Long> ids(String sql, Long userId, Long roomId, int limit) {
    List<Long> ids = new ArrayList<>();
    for (Object id :
        em.createNativeQuery(sql)
            .setParameter("user", userId)
            .setParameter("room", roomId)
            .setMaxResults(limit)
            .getResultList()) {
      ids.add(((Number) id).longValue());
    }
    return ids;
  }
}
