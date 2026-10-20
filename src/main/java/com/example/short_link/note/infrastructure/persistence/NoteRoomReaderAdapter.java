package com.example.short_link.note.infrastructure.persistence;

import com.example.short_link.note.domain.repository.NoteRoomReader;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Repository;

// Rooms live in the room module; a note reads its room's name and membership straight from the
// tables.
@Repository
class NoteRoomReaderAdapter implements NoteRoomReader {

  @PersistenceContext private EntityManager em;

  @Override
  public boolean isMember(Long roomId, Long userId) {
    return !em.createNativeQuery(
            "SELECT 1 FROM room_member WHERE room_id = :room AND user_id = :user")
        .setParameter("room", roomId)
        .setParameter("user", userId)
        .getResultList()
        .isEmpty();
  }

  @Override
  public Map<Long, String> names(Collection<Long> roomIds) {
    Map<Long, String> names = new HashMap<>();
    if (roomIds.isEmpty()) {
      return names;
    }
    for (Object raw :
        em.createNativeQuery("SELECT id, name FROM room WHERE id IN (:ids)")
            .setParameter("ids", roomIds)
            .getResultList()) {
      Object[] row = (Object[]) raw;
      names.put(((Number) row[0]).longValue(), (String) row[1]);
    }
    return names;
  }
}
