package com.example.short_link.note.infrastructure.persistence;

import com.example.short_link.common.note.NoteAccessSql;
import com.example.short_link.note.domain.RoomErasure;
import com.example.short_link.note.domain.repository.RoomNoteErasureRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
class RoomNoteErasureRepositoryAdapter implements RoomNoteErasureRepository {

  @PersistenceContext private EntityManager em;

  @Override
  public List<Long> roomNoteIdsBy(Long authorId, Long roomId, int limit) {
    List<?> rows =
        em.createNativeQuery(
                "SELECT n.id FROM note n WHERE n.user_id = :author AND n.room_id = :room AND "
                    + NoteAccessSql.inRoom("n")
                    + " ORDER BY n.id DESC")
            .setParameter("author", authorId)
            .setParameter("room", roomId)
            .setMaxResults(limit)
            .getResultList();
    return rows.stream().map(id -> ((Number) id).longValue()).toList();
  }

  @Override
  public void requestRoomErasure(Long userId, Long roomId, Instant at) {
    em.createNativeQuery(
            "INSERT IGNORE INTO room_note_erasure (user_id, room_id, requested_at)"
                + " VALUES (:user, :room, :at)")
        .setParameter("user", userId)
        .setParameter("room", roomId)
        .setParameter("at", at)
        .executeUpdate();
  }

  @Override
  public List<RoomErasure> pendingRoomErasures(int limit) {
    List<?> rows =
        em.createNativeQuery(
                "SELECT user_id, room_id FROM room_note_erasure ORDER BY requested_at, user_id")
            .setMaxResults(limit)
            .getResultList();
    List<RoomErasure> pending = new ArrayList<>(rows.size());
    for (Object raw : rows) {
      Object[] cols = (Object[]) raw;
      pending.add(new RoomErasure(((Number) cols[0]).longValue(), ((Number) cols[1]).longValue()));
    }
    return pending;
  }

  @Override
  public void finishRoomErasure(Long userId, Long roomId) {
    em.createNativeQuery("DELETE FROM room_note_erasure WHERE user_id = :user AND room_id = :room")
        .setParameter("user", userId)
        .setParameter("room", roomId)
        .executeUpdate();
  }
}
