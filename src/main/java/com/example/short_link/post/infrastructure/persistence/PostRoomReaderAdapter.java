package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.post.domain.repository.PostRoomReader;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Repository;

@Repository
class PostRoomReaderAdapter implements PostRoomReader {

  @PersistenceContext private EntityManager em;

  @Override
  public boolean isMember(Long roomId, Long userId) {
    if (roomId == null || userId == null) {
      return false;
    }
    return !em.createNativeQuery(
            "SELECT 1 FROM room_member WHERE room_id = :room AND user_id = :user")
        .setParameter("room", roomId)
        .setParameter("user", userId)
        .getResultList()
        .isEmpty();
  }

  @Override
  public Set<Long> roomsOf(Long userId) {
    Set<Long> rooms = new HashSet<>();
    if (userId == null) {
      return rooms;
    }
    for (Object id :
        em.createNativeQuery("SELECT room_id FROM room_member WHERE user_id = :user")
            .setParameter("user", userId)
            .getResultList()) {
      rooms.add(((Number) id).longValue());
    }
    return rooms;
  }

  @Override
  public Set<Long> membersAmong(Long roomId, Collection<Long> userIds) {
    Set<Long> members = new HashSet<>();
    if (roomId == null || userIds.isEmpty()) {
      return members;
    }
    for (Object id :
        em.createNativeQuery(
                "SELECT user_id FROM room_member WHERE room_id = :room AND user_id IN (:users)")
            .setParameter("room", roomId)
            .setParameter("users", userIds)
            .getResultList()) {
      members.add(((Number) id).longValue());
    }
    return members;
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
