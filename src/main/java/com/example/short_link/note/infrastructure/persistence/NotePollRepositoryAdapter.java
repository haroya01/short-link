package com.example.short_link.note.infrastructure.persistence;

import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.NotePollTally;
import com.example.short_link.note.domain.repository.NotePollRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

@Repository
class NotePollRepositoryAdapter implements NotePollRepository {

  @PersistenceContext private EntityManager em;

  @Override
  public Map<Long, NotePollTally> tallies(Collection<Long> noteIds, Long viewerId) {
    Map<Long, NotePollTally> tallies = new HashMap<>();
    if (noteIds.isEmpty()) {
      return tallies;
    }
    List<?> rows =
        em.createNativeQuery(
                "SELECT v.note_id, COUNT(*),"
                    + " SUM(v.choices & 1), SUM((v.choices >> 1) & 1),"
                    + " SUM((v.choices >> 2) & 1), SUM((v.choices >> 3) & 1),"
                    + " MAX(CASE WHEN v.user_id = :viewer THEN v.choices END)"
                    + " FROM (SELECT note_id, user_id, choices FROM note_poll_vote"
                    + " WHERE note_id IN (:ids)"
                    + " UNION ALL SELECT note_id, NULL, choices FROM note_poll_remote_vote"
                    + " WHERE note_id IN (:ids)) v GROUP BY v.note_id")
            .setParameter("ids", noteIds)
            .setParameter("viewer", viewerId == null ? -1L : viewerId)
            .getResultList();
    for (Object raw : rows) {
      Object[] cols = (Object[]) raw;
      tallies.put(
          ((Number) cols[0]).longValue(),
          new NotePollTally(
              ((Number) cols[1]).longValue(),
              List.of(count(cols[2]), count(cols[3]), count(cols[4]), count(cols[5])),
              cols[6] == null ? null : ((Number) cols[6]).intValue()));
    }
    return tallies;
  }

  private static long count(Object value) {
    return value == null ? 0 : ((Number) value).longValue();
  }

  @Override
  public boolean vote(Long noteId, Long userId, int choices) {
    return em.createNativeQuery(
                "INSERT IGNORE INTO note_poll_vote (note_id, user_id, choices, created_at)"
                    + " VALUES (:note, :user, :choices, NOW(6))")
            .setParameter("note", noteId)
            .setParameter("user", userId)
            .setParameter("choices", choices)
            .executeUpdate()
        == 1;
  }

  // Mastodon sends one Create per chosen option of a multiple-choice poll, so those fold into the
  // voter's one row; a single-choice poll keeps the first vote.
  @Override
  public boolean voteRemote(Long noteId, Long remoteActorId, int choices, boolean multiple) {
    String sql =
        multiple
            ? "INSERT INTO note_poll_remote_vote (note_id, remote_actor_id, choices, created_at)"
                + " VALUES (:note, :actor, :choices, NOW(6)) AS fresh"
                + " ON DUPLICATE KEY UPDATE choices = note_poll_remote_vote.choices | fresh.choices"
            : "INSERT IGNORE INTO note_poll_remote_vote"
                + " (note_id, remote_actor_id, choices, created_at)"
                + " VALUES (:note, :actor, :choices, NOW(6))";
    return em.createNativeQuery(sql)
            .setParameter("note", noteId)
            .setParameter("actor", remoteActorId)
            .setParameter("choices", choices)
            .executeUpdate()
        > 0;
  }

  @Override
  public Map<Long, List<Long>> voterIds(Collection<Long> noteIds) {
    Map<Long, List<Long>> voters = new HashMap<>();
    if (noteIds.isEmpty()) {
      return voters;
    }
    List<?> rows =
        em.createNativeQuery("SELECT note_id, user_id FROM note_poll_vote WHERE note_id IN (:ids)")
            .setParameter("ids", noteIds)
            .getResultList();
    for (Object raw : rows) {
      Object[] cols = (Object[]) raw;
      voters
          .computeIfAbsent(((Number) cols[0]).longValue(), id -> new ArrayList<>())
          .add(((Number) cols[1]).longValue());
    }
    return voters;
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<NoteEntity> due(Instant now, int limit) {
    return em.createNativeQuery(
            "SELECT * FROM note WHERE poll_closed_at IS NULL AND poll_expires_at <= :now"
                + " ORDER BY poll_expires_at",
            NoteEntity.class)
        .setParameter("now", now)
        .setMaxResults(limit)
        .getResultList();
  }

  @Override
  public void close(Collection<Long> noteIds, Instant at) {
    if (noteIds.isEmpty()) {
      return;
    }
    em.createNativeQuery("UPDATE note SET poll_closed_at = :at WHERE id IN (:ids)")
        .setParameter("at", at)
        .setParameter("ids", noteIds)
        .executeUpdate();
  }
}
