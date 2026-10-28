package com.example.short_link.room.infrastructure.persistence;

import static com.example.short_link.room.infrastructure.persistence.RoomPeopleRows.PERSON;
import static com.example.short_link.room.infrastructure.persistence.RoomPeopleRows.people;
import static com.example.short_link.room.infrastructure.persistence.RoomPeopleRows.person;
import static com.example.short_link.room.infrastructure.persistence.RoomPeopleRows.truth;

import com.example.short_link.common.note.Excerpt;
import com.example.short_link.room.domain.RoomEntity;
import com.example.short_link.room.domain.RoomPerson;
import com.example.short_link.room.domain.RoomSummary;
import com.example.short_link.room.domain.repository.RoomRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.hibernate.query.NativeQuery;
import org.springframework.stereotype.Repository;

@Repository
class RoomRepositoryAdapter implements RoomRepository {

  @PersistenceContext private EntityManager em;

  @Override
  public RoomEntity save(RoomEntity room) {
    em.persist(room);
    return room;
  }

  @Override
  public Optional<RoomEntity> findById(Long roomId) {
    return Optional.ofNullable(em.find(RoomEntity.class, roomId));
  }

  @Override
  public Optional<RoomEntity> lock(Long roomId) {
    return Optional.ofNullable(em.find(RoomEntity.class, roomId, LockModeType.PESSIMISTIC_WRITE));
  }

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
  public long countMembers(Long roomId) {
    return ((Number)
            em.createNativeQuery("SELECT COUNT(*) FROM room_member WHERE room_id = :room")
                .setParameter("room", roomId)
                .getSingleResult())
        .longValue();
  }

  @Override
  public void addMember(Long roomId, Long userId, Instant at) {
    em.createNativeQuery(
            "INSERT IGNORE INTO room_member (room_id, user_id, joined_at, last_read_note_id)"
                + " SELECT :room, :user, :at, COALESCE(MAX(n.id), 0) FROM note n"
                + " WHERE n.room_id = :room")
        .setParameter("room", roomId)
        .setParameter("user", userId)
        .setParameter("at", at)
        .executeUpdate();
  }

  @Override
  public void removeMember(Long roomId, Long userId) {
    em.createNativeQuery("DELETE FROM room_member WHERE room_id = :room AND user_id = :user")
        .setParameter("room", roomId)
        .setParameter("user", userId)
        .executeUpdate();
  }

  @Override
  public List<RoomPerson> members(Long roomId, int limit) {
    return people(
        em.createNativeQuery(
                "SELECT "
                    + PERSON
                    + " FROM room_member m JOIN users u ON u.id = m.user_id"
                    + " WHERE m.room_id = :room AND u.deleted_at IS NULL"
                    + " ORDER BY m.joined_at, u.id")
            .setParameter("room", roomId)
            .setMaxResults(limit)
            .getResultList());
  }

  @Override
  public Optional<Long> longestStayingMemberBesides(Long roomId, Long userId) {
    return em
        .createNativeQuery(
            "SELECT m.user_id FROM room_member m WHERE m.room_id = :room AND m.user_id <> :user"
                + " ORDER BY m.joined_at, m.user_id")
        .setParameter("room", roomId)
        .setParameter("user", userId)
        .setMaxResults(1)
        .getResultList()
        .stream()
        .findFirst()
        .map(id -> ((Number) id).longValue());
  }

  @Override
  public boolean hiddenFromFeed(Long roomId, Long userId) {
    return em
        .createNativeQuery(
            "SELECT hidden_from_feed FROM room_member WHERE room_id = :room AND user_id = :user")
        .setParameter("room", roomId)
        .setParameter("user", userId)
        .getResultList()
        .stream()
        .anyMatch(RoomPeopleRows::truth);
  }

  @Override
  public void hideFromFeed(Long roomId, Long userId, boolean hidden) {
    em.createNativeQuery(
            "UPDATE room_member SET hidden_from_feed = :hidden"
                + " WHERE room_id = :room AND user_id = :user")
        .setParameter("hidden", hidden)
        .setParameter("room", roomId)
        .setParameter("user", userId)
        .executeUpdate();
  }

  @Override
  public long countPinned(Long userId) {
    return ((Number)
            em.createNativeQuery(
                    "SELECT COUNT(*) FROM room_member WHERE user_id = :user AND pinned_at IS NOT NULL")
                .setParameter("user", userId)
                .getSingleResult())
        .longValue();
  }

  @Override
  public boolean isPinned(Long roomId, Long userId) {
    return !em.createNativeQuery(
            "SELECT 1 FROM room_member WHERE room_id = :room AND user_id = :user"
                + " AND pinned_at IS NOT NULL")
        .setParameter("room", roomId)
        .setParameter("user", userId)
        .getResultList()
        .isEmpty();
  }

  @Override
  public void pin(Long roomId, Long userId, Instant at) {
    pinnedAt(roomId, userId, at);
  }

  @Override
  public void unpin(Long roomId, Long userId) {
    pinnedAt(roomId, userId, null);
  }

  private void pinnedAt(Long roomId, Long userId, Instant at) {
    em.createNativeQuery(
            "UPDATE room_member SET pinned_at = :at WHERE room_id = :room AND user_id = :user")
        .setParameter("at", at)
        .setParameter("room", roomId)
        .setParameter("user", userId)
        .executeUpdate();
  }

  @Override
  public void readUpTo(Long roomId, Long userId, Long noteId) {
    em.createNativeQuery(
            "UPDATE room_member SET last_read_note_id = GREATEST(last_read_note_id, :note)"
                + " WHERE room_id = :room AND user_id = :user")
        .setParameter("note", noteId)
        .setParameter("room", roomId)
        .setParameter("user", userId)
        .executeUpdate();
  }

  @Override
  public long unread(Long roomId, Long userId) {
    return ((Number)
            em.createNativeQuery(
                    "SELECT COUNT(*) FROM room_member m JOIN note n ON n.room_id = m.room_id"
                        + unreadBy("n")
                        + " WHERE m.room_id = :room AND m.user_id = :user")
                .setParameter("room", roomId)
                .setParameter("user", userId)
                .getSingleResult())
        .longValue();
  }

  @Override
  @SuppressWarnings("unchecked")
  public Map<Long, List<RoomPerson>> faces(
      Collection<Long> roomIds, Long besidesUserId, int perRoom) {
    if (roomIds.isEmpty()) {
      return Map.of();
    }
    List<Object[]> rows =
        em.createNativeQuery(
                "SELECT f.id, f.username, f.display_name, f.avatar_url, f.room_id FROM ("
                    + " SELECT u.id, u.username, u.display_name, u.avatar_url, m.room_id,"
                    + " ROW_NUMBER() OVER (PARTITION BY m.room_id ORDER BY m.joined_at, u.id) AS rn"
                    + " FROM room_member m JOIN users u ON u.id = m.user_id"
                    + " WHERE m.room_id IN (:rooms) AND m.user_id <> :user"
                    + " AND u.deleted_at IS NULL) f"
                    + " WHERE f.rn <= :per ORDER BY f.room_id, f.rn")
            .setParameter("rooms", roomIds)
            .setParameter("user", besidesUserId)
            .setParameter("per", perRoom)
            .getResultList();
    Map<Long, List<RoomPerson>> faces = new HashMap<>();
    for (Object[] c : rows) {
      faces.computeIfAbsent(((Number) c[4]).longValue(), room -> new ArrayList<>()).add(person(c));
    }
    return faces;
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<RoomSummary> summaries(Long userId) {
    List<Object[]> rows =
        em.createNativeQuery(
                "SELECT r.id AS id, r.name AS name, r.owner_id AS owner, m.hidden_from_feed AS hidden,"
                    + " m.pinned_at IS NOT NULL AS pinned,"
                    + " (SELECT COUNT(*) FROM room_member c WHERE c.room_id = r.id) AS members,"
                    + " (SELECT COUNT(*) FROM note un WHERE un.room_id = r.id"
                    + unreadBy("un")
                    + ") AS unread,"
                    + " COALESCE(ln.created_at, r.created_at) AS active_at,"
                    + " ln.id AS note_id, ln.body AS body, ln.content_warning AS warning,"
                    + " ln.created_at AS note_at, lu.id AS author_id, lu.username AS username,"
                    + " lu.display_name AS display_name, lu.avatar_url AS avatar_url"
                    + " FROM room_member m JOIN room r ON r.id = m.room_id"
                    + " LEFT JOIN note ln ON ln.id ="
                    + " (SELECT MAX(x.id) FROM note x WHERE x.room_id = r.id)"
                    + " LEFT JOIN users lu ON lu.id = ln.user_id"
                    + " WHERE m.user_id = :user"
                    + " ORDER BY m.pinned_at IS NULL, active_at DESC, r.id DESC")
            .setParameter("user", userId)
            .unwrap(NativeQuery.class)
            .addScalar("id", Long.class)
            .addScalar("name", String.class)
            .addScalar("owner", Long.class)
            .addScalar("hidden", Boolean.class)
            .addScalar("pinned", Long.class)
            .addScalar("members", Long.class)
            .addScalar("unread", Long.class)
            .addScalar("active_at", Instant.class)
            .addScalar("note_id", Long.class)
            .addScalar("body", String.class)
            .addScalar("warning", String.class)
            .addScalar("note_at", Instant.class)
            .addScalar("author_id", Long.class)
            .addScalar("username", String.class)
            .addScalar("display_name", String.class)
            .addScalar("avatar_url", String.class)
            .getResultList();
    List<RoomSummary> summaries = new ArrayList<>(rows.size());
    for (Object[] c : rows) {
      RoomSummary.LastNote last =
          c[8] == null
              ? null
              : new RoomSummary.LastNote(
                  (Long) c[8],
                  excerpt((String) c[9], (String) c[10]),
                  c[12] == null
                      ? null
                      : new RoomPerson(
                          (Long) c[12], (String) c[13], (String) c[14], (String) c[15]),
                  (Instant) c[11]);
      summaries.add(
          new RoomSummary(
              (Long) c[0],
              (String) c[1],
              userId.equals(c[2]),
              (Long) c[5],
              Boolean.TRUE.equals(c[3]),
              truth(c[4]),
              (Long) c[6],
              (Instant) c[7],
              last));
    }
    return summaries;
  }

  @Override
  public void markRemoved(Long roomId, Long userId, Instant at) {
    em.createNativeQuery(
            "INSERT IGNORE INTO room_removal (room_id, user_id, created_at)"
                + " VALUES (:room, :user, :at)")
        .setParameter("room", roomId)
        .setParameter("user", userId)
        .setParameter("at", at)
        .executeUpdate();
  }

  @Override
  public boolean isRemoved(Long roomId, Long userId) {
    return !em.createNativeQuery(
            "SELECT 1 FROM room_removal WHERE room_id = :room AND user_id = :user")
        .setParameter("room", roomId)
        .setParameter("user", userId)
        .getResultList()
        .isEmpty();
  }

  private static String unreadBy(String note) {
    return " AND "
        + note
        + ".id > m.last_read_note_id AND NOT ("
        + note
        + ".user_id <=> m.user_id)";
  }

  private static String excerpt(String body, String warning) {
    return Excerpt.of(body, warning);
  }
}
