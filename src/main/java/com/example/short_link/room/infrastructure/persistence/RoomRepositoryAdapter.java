package com.example.short_link.room.infrastructure.persistence;

import com.example.short_link.common.note.Excerpt;
import com.example.short_link.common.user.HeardSql;
import com.example.short_link.room.domain.RoomEntity;
import com.example.short_link.room.domain.RoomInvitation;
import com.example.short_link.room.domain.RoomInviteEntity;
import com.example.short_link.room.domain.RoomInvitee;
import com.example.short_link.room.domain.RoomJoinRequest;
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

  private static final String PERSON = "u.id, u.username, u.display_name, u.avatar_url";

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
        .anyMatch(RoomRepositoryAdapter::truth);
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
  public RoomInviteEntity save(RoomInviteEntity invite) {
    em.persist(invite);
    return invite;
  }

  @Override
  public Optional<RoomInviteEntity> inviteByCode(String code) {
    return em.createQuery(
            "select i from RoomInviteEntity i where i.code = :code", RoomInviteEntity.class)
        .setParameter("code", code)
        .getResultStream()
        .findFirst();
  }

  @Override
  public Optional<RoomInviteEntity> lockInvite(String code) {
    return em.createQuery(
            "select i from RoomInviteEntity i where i.code = :code", RoomInviteEntity.class)
        .setParameter("code", code)
        .setLockMode(LockModeType.PESSIMISTIC_WRITE)
        .getResultStream()
        .findFirst();
  }

  @Override
  public Optional<RoomInviteEntity> invite(Long roomId, Long inviteId) {
    return Optional.ofNullable(em.find(RoomInviteEntity.class, inviteId))
        .filter(invite -> invite.getRoomId().equals(roomId));
  }

  @Override
  public List<RoomInviteEntity> invites(Long roomId) {
    return em.createQuery(
            "select i from RoomInviteEntity i where i.roomId = :room order by i.id desc",
            RoomInviteEntity.class)
        .setParameter("room", roomId)
        .getResultList();
  }

  @Override
  public void disableInvitesBy(Long roomId, Long creatorId, Instant at) {
    em.createNativeQuery(
            "UPDATE room_invite SET disabled_at = :at"
                + " WHERE room_id = :room AND creator_id = :creator AND disabled_at IS NULL")
        .setParameter("at", at)
        .setParameter("room", roomId)
        .setParameter("creator", creatorId)
        .executeUpdate();
  }

  @Override
  public boolean request(Long roomId, Long userId, Long inviteId, Instant at) {
    return em.createNativeQuery(
                "INSERT IGNORE INTO room_join_request (room_id, user_id, invite_id, created_at)"
                    + " VALUES (:room, :user, :invite, :at)")
            .setParameter("room", roomId)
            .setParameter("user", userId)
            .setParameter("invite", inviteId)
            .setParameter("at", at)
            .executeUpdate()
        > 0;
  }

  @Override
  public boolean hasRequested(Long roomId, Long userId) {
    return !em.createNativeQuery(
            "SELECT 1 FROM room_join_request WHERE room_id = :room AND user_id = :user")
        .setParameter("room", roomId)
        .setParameter("user", userId)
        .getResultList()
        .isEmpty();
  }

  @Override
  public boolean withdrawRequest(Long roomId, Long userId) {
    return em.createNativeQuery(
                "DELETE FROM room_join_request WHERE room_id = :room AND user_id = :user")
            .setParameter("room", roomId)
            .setParameter("user", userId)
            .executeUpdate()
        > 0;
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<RoomJoinRequest> requests(Long roomId) {
    List<Object[]> rows =
        em.createNativeQuery(
                "SELECT u.id AS id, u.username AS username, u.display_name AS display_name,"
                    + " u.avatar_url AS avatar_url, q.created_at AS requested_at"
                    + " FROM room_join_request q JOIN users u ON u.id = q.user_id"
                    + " WHERE q.room_id = :room AND u.deleted_at IS NULL ORDER BY q.created_at")
            .setParameter("room", roomId)
            .unwrap(NativeQuery.class)
            .addScalar("id", Long.class)
            .addScalar("username", String.class)
            .addScalar("display_name", String.class)
            .addScalar("avatar_url", String.class)
            .addScalar("requested_at", Instant.class)
            .getResultList();
    List<RoomJoinRequest> requests = new ArrayList<>(rows.size());
    for (Object[] c : rows) {
      requests.add(new RoomJoinRequest(person(c), (Instant) c[4]));
    }
    return requests;
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

  @Override
  public Optional<RoomPerson> person(Long userId) {
    return people(
            em.createNativeQuery(
                    "SELECT "
                        + PERSON
                        + " FROM users u WHERE u.id = :user AND u.deleted_at IS NULL")
                .setParameter("user", userId)
                .getResultList())
        .stream()
        .findFirst();
  }

  @Override
  public List<RoomPerson> friendsWithoutARoom(Long userId, int limit) {
    return people(
        em.createNativeQuery(
                "SELECT "
                    + PERSON
                    + " FROM user_follow a"
                    + " JOIN user_follow b ON b.follower_id = a.following_id"
                    + " AND b.following_id = a.follower_id"
                    + " JOIN users u ON u.id = a.following_id"
                    + " AND u.deleted_at IS NULL AND u.username IS NOT NULL"
                    + " WHERE a.follower_id = :viewer"
                    + " AND NOT EXISTS (SELECT 1 FROM room_suggestion_dismissal d"
                    + " WHERE d.user_id = :viewer AND d.friend_id = u.id)"
                    + " AND NOT EXISTS (SELECT 1 FROM room_member mine"
                    + " JOIN room_member theirs ON theirs.room_id = mine.room_id"
                    + " WHERE mine.user_id = :viewer AND theirs.user_id = u.id)"
                    + HeardSql.unblocked("u.id")
                    + " ORDER BY GREATEST(a.created_at, b.created_at) DESC, u.id DESC")
            .setParameter("viewer", userId)
            .setMaxResults(limit)
            .getResultList());
  }

  @Override
  public void dismissSuggestions(Long userId, Collection<Long> friendIds, Instant at) {
    for (Long friendId : friendIds) {
      em.createNativeQuery(
              "INSERT IGNORE INTO room_suggestion_dismissal (user_id, friend_id, created_at)"
                  + " VALUES (:user, :friend, :at)")
          .setParameter("user", userId)
          .setParameter("friend", friendId)
          .setParameter("at", at)
          .executeUpdate();
    }
  }

  @Override
  public int addInvitations(Long roomId, Long inviterId, Collection<Long> userIds, Instant at) {
    return em.createNativeQuery(
            "INSERT IGNORE INTO room_invitation (room_id, user_id, inviter_id, created_at)"
                + " SELECT :room, u.id, :viewer, :at FROM users u"
                + " WHERE u.id IN (:ids) AND u.id <> :viewer"
                + " AND u.deleted_at IS NULL AND u.username IS NOT NULL"
                + " AND NOT EXISTS (SELECT 1 FROM room_member m"
                + " WHERE m.room_id = :room AND m.user_id = u.id)"
                + " AND NOT EXISTS (SELECT 1 FROM room_removal r"
                + " WHERE r.room_id = :room AND r.user_id = u.id)"
                + HeardSql.unblocked("u.id"))
        .setParameter("room", roomId)
        .setParameter("viewer", inviterId)
        .setParameter("ids", userIds)
        .setParameter("at", at)
        .executeUpdate();
  }

  @Override
  public long countInvitees(Long roomId) {
    return ((Number)
            em.createNativeQuery(
                    "SELECT COUNT(*) FROM room_invitation"
                        + " WHERE room_id = :room AND declined_at IS NULL")
                .setParameter("room", roomId)
                .getSingleResult())
        .longValue();
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<RoomInvitee> invitees(Long roomId) {
    List<Object[]> rows =
        em.createNativeQuery(
                "SELECT u.id AS id, u.username AS username, u.display_name AS display_name,"
                    + " u.avatar_url AS avatar_url, i.inviter_id AS inviter_id,"
                    + " i.created_at AS invited_at"
                    + " FROM room_invitation i JOIN users u ON u.id = i.user_id"
                    + " WHERE i.room_id = :room AND i.declined_at IS NULL"
                    + " AND u.deleted_at IS NULL ORDER BY i.created_at DESC, u.id DESC")
            .setParameter("room", roomId)
            .unwrap(NativeQuery.class)
            .addScalar("id", Long.class)
            .addScalar("username", String.class)
            .addScalar("display_name", String.class)
            .addScalar("avatar_url", String.class)
            .addScalar("inviter_id", Long.class)
            .addScalar("invited_at", Instant.class)
            .getResultList();
    List<RoomInvitee> invitees = new ArrayList<>(rows.size());
    for (Object[] c : rows) {
      invitees.add(new RoomInvitee(person(c), (Long) c[4], (Instant) c[5]));
    }
    return invitees;
  }

  @Override
  public Optional<Long> inviterOf(Long roomId, Long userId) {
    return em
        .createNativeQuery(
            "SELECT inviter_id FROM room_invitation"
                + " WHERE room_id = :room AND user_id = :user AND declined_at IS NULL")
        .setParameter("room", roomId)
        .setParameter("user", userId)
        .getResultList()
        .stream()
        .findFirst()
        .map(id -> ((Number) id).longValue());
  }

  @Override
  public boolean dropInvitation(Long roomId, Long userId) {
    return em.createNativeQuery(
                "DELETE FROM room_invitation"
                    + " WHERE room_id = :room AND user_id = :user AND declined_at IS NULL")
            .setParameter("room", roomId)
            .setParameter("user", userId)
            .executeUpdate()
        > 0;
  }

  @Override
  public int dropInvitationsBy(Long roomId, Long inviterId) {
    return em.createNativeQuery(
            "DELETE FROM room_invitation"
                + " WHERE room_id = :room AND inviter_id = :inviter AND declined_at IS NULL")
        .setParameter("room", roomId)
        .setParameter("inviter", inviterId)
        .executeUpdate();
  }

  @Override
  public boolean declineInvitation(Long roomId, Long userId, Instant at) {
    return em.createNativeQuery(
                "UPDATE room_invitation SET declined_at = :at"
                    + " WHERE room_id = :room AND user_id = :user AND declined_at IS NULL")
            .setParameter("room", roomId)
            .setParameter("user", userId)
            .setParameter("at", at)
            .executeUpdate()
        > 0;
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<RoomInvitation> invitationsFor(Long userId, int limit) {
    List<Object[]> rows =
        em.createNativeQuery(
                "SELECT u.id AS id, u.username AS username, u.display_name AS display_name,"
                    + " u.avatar_url AS avatar_url, r.id AS room_id, r.name AS room_name,"
                    + " (SELECT COUNT(*) FROM room_member c WHERE c.room_id = r.id) AS members,"
                    + " (r.approval_required AND r.owner_id <> i.inviter_id) AS approval,"
                    + " i.created_at AS invited_at"
                    + " FROM room_invitation i JOIN room r ON r.id = i.room_id"
                    + " JOIN users u ON u.id = i.inviter_id AND u.deleted_at IS NULL"
                    + " WHERE i.user_id = :viewer AND i.declined_at IS NULL"
                    + " AND NOT EXISTS (SELECT 1 FROM room_member m"
                    + " WHERE m.room_id = i.room_id AND m.user_id = :viewer)"
                    + HeardSql.unblocked("i.inviter_id")
                    + " ORDER BY i.created_at DESC, i.room_id DESC")
            .setParameter("viewer", userId)
            .setMaxResults(limit)
            .unwrap(NativeQuery.class)
            .addScalar("id", Long.class)
            .addScalar("username", String.class)
            .addScalar("display_name", String.class)
            .addScalar("avatar_url", String.class)
            .addScalar("room_id", Long.class)
            .addScalar("room_name", String.class)
            .addScalar("members", Long.class)
            .addScalar("approval", Long.class)
            .addScalar("invited_at", Instant.class)
            .getResultList();
    List<RoomInvitation> invitations = new ArrayList<>(rows.size());
    for (Object[] c : rows) {
      invitations.add(
          new RoomInvitation(
              (Long) c[4], (String) c[5], (Long) c[6], person(c), truth(c[7]), (Instant) c[8]));
    }
    return invitations;
  }

  private static List<RoomPerson> people(List<?> rows) {
    List<RoomPerson> people = new ArrayList<>(rows.size());
    for (Object raw : rows) {
      people.add(person((Object[]) raw));
    }
    return people;
  }

  private static RoomPerson person(Object[] c) {
    return new RoomPerson(((Number) c[0]).longValue(), (String) c[1], (String) c[2], (String) c[3]);
  }

  private static boolean truth(Object value) {
    return value instanceof Boolean b ? b : value != null && ((Number) value).intValue() != 0;
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
