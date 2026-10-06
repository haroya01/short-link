package com.example.short_link.note.infrastructure.persistence;

import com.example.short_link.common.note.RemoteNoteReactions.Kind;
import com.example.short_link.note.domain.repository.NoteRemoteReactionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
class NoteRemoteReactionRepositoryAdapter implements NoteRemoteReactionRepository {

  private final JpaNoteRemoteReactionRepository jpa;

  @PersistenceContext private EntityManager em;

  // A re-sent reaction keeps its one row and takes the newest activity id, the one a later Undo
  // names.
  @Override
  @Transactional
  public void put(Long noteId, Long remoteActorId, Kind kind, String activityId) {
    em.createNativeQuery(
            "INSERT INTO note_remote_reaction"
                + " (note_id, remote_actor_id, kind, activity_id, created_at)"
                + " VALUES (:noteId, :actorId, :kind, :activityId, NOW(6)) AS fresh"
                + " ON DUPLICATE KEY UPDATE activity_id = fresh.activity_id")
        .setParameter("noteId", noteId)
        .setParameter("actorId", remoteActorId)
        .setParameter("kind", kind.name())
        .setParameter("activityId", activityId)
        .executeUpdate();
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

  @Override
  public long count(Long noteId, Kind kind) {
    return jpa.countByNoteIdAndKind(noteId, kind);
  }

  @Override
  public Map<Kind, Map<Long, Long>> counts(Collection<Long> noteIds) {
    Map<Kind, Map<Long, Long>> counts = new EnumMap<>(Kind.class);
    for (Kind kind : Kind.values()) {
      counts.put(kind, new HashMap<>());
    }
    if (noteIds.isEmpty()) {
      return counts;
    }
    for (Object[] row : jpa.counts(noteIds)) {
      counts.get((Kind) row[1]).put((Long) row[0], (Long) row[2]);
    }
    return counts;
  }
}
