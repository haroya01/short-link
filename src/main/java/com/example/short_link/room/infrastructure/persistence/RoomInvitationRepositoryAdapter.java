package com.example.short_link.room.infrastructure.persistence;

import static com.example.short_link.room.infrastructure.persistence.RoomPeopleRows.person;
import static com.example.short_link.room.infrastructure.persistence.RoomPeopleRows.truth;

import com.example.short_link.common.user.HeardSql;
import com.example.short_link.room.domain.RoomInvitation;
import com.example.short_link.room.domain.RoomInvitee;
import com.example.short_link.room.domain.repository.RoomInvitationRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.hibernate.query.NativeQuery;
import org.springframework.stereotype.Repository;

@Repository
class RoomInvitationRepositoryAdapter implements RoomInvitationRepository {

  @PersistenceContext private EntityManager em;

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
}
