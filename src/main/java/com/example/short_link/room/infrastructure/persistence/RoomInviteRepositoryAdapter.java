package com.example.short_link.room.infrastructure.persistence;

import static com.example.short_link.room.infrastructure.persistence.RoomPeopleRows.PERSON;
import static com.example.short_link.room.infrastructure.persistence.RoomPeopleRows.people;

import com.example.short_link.room.domain.RoomInviteEntity;
import com.example.short_link.room.domain.RoomJoinRequest;
import com.example.short_link.room.domain.RoomPerson;
import com.example.short_link.room.domain.repository.RoomInviteRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.hibernate.query.NativeQuery;
import org.springframework.stereotype.Repository;

@Repository
class RoomInviteRepositoryAdapter implements RoomInviteRepository {

  @PersistenceContext private EntityManager em;

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
      requests.add(new RoomJoinRequest(RoomPeopleRows.person(c), (Instant) c[4]));
    }
    return requests;
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
}
