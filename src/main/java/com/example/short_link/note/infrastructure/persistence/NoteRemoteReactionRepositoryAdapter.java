package com.example.short_link.note.infrastructure.persistence;

import com.example.short_link.common.note.RemoteNoteReactions.Kind;
import com.example.short_link.note.domain.repository.NoteRemoteReactionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
class NoteRemoteReactionRepositoryAdapter implements NoteRemoteReactionRepository {

  @PersistenceContext private EntityManager em;

  // True only for a new row. A re-sent reaction keeps its one row and takes the newest activity id,
  // the one a later Undo names. ON DUPLICATE KEY UPDATE cannot tell a new row from an unchanged one
  // under the driver's found-rows count, so the insert and the refresh are separate statements.
  @Override
  @Transactional
  public boolean add(Long noteId, Long remoteActorId, Kind kind, String activityId) {
    int inserted =
        em.createNativeQuery(
                "INSERT IGNORE INTO note_remote_reaction"
                    + " (note_id, remote_actor_id, kind, activity_id, created_at)"
                    + " VALUES (:noteId, :actorId, :kind, :activityId, NOW(6))")
            .setParameter("noteId", noteId)
            .setParameter("actorId", remoteActorId)
            .setParameter("kind", kind.name())
            .setParameter("activityId", activityId)
            .executeUpdate();
    if (inserted == 1) {
      return true;
    }
    em.createNativeQuery(
            "UPDATE note_remote_reaction SET activity_id = :activityId"
                + " WHERE note_id = :noteId AND remote_actor_id = :actorId AND kind = :kind")
        .setParameter("noteId", noteId)
        .setParameter("actorId", remoteActorId)
        .setParameter("kind", kind.name())
        .setParameter("activityId", activityId)
        .executeUpdate();
    return false;
  }

  @Override
  @Transactional
  public void delete(Long noteId, Long remoteActorId, Kind kind) {
    em.createNativeQuery(
            "DELETE FROM note_remote_reaction"
                + " WHERE note_id = :noteId AND remote_actor_id = :actorId AND kind = :kind")
        .setParameter("noteId", noteId)
        .setParameter("actorId", remoteActorId)
        .setParameter("kind", kind.name())
        .executeUpdate();
  }

  @Override
  @Transactional
  public void deleteByActivity(Long remoteActorId, String activityId) {
    em.createNativeQuery(
            "DELETE FROM note_remote_reaction"
                + " WHERE remote_actor_id = :actorId AND activity_id = :activityId")
        .setParameter("actorId", remoteActorId)
        .setParameter("activityId", activityId)
        .executeUpdate();
  }
}
