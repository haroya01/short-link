package com.example.short_link.federation.infrastructure.persistence;

import com.example.short_link.federation.domain.repository.FederationRelayedNoteRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
class FederationRelayedNoteRepositoryAdapter implements FederationRelayedNoteRepository {

  // A member's touch keeps the note: a like, repost, bookmark or poll vote, a reply or quote of
  // their own, a post quoting it, or a follow of its author. The candidates sit in a derived table
  // so MySQL materializes them before deleting from note, which they also read.
  private static final String DELETE_EXPIRED =
      "DELETE FROM note WHERE id IN (SELECT note_id FROM ("
          + "SELECT r.note_id FROM federation_relayed_note r"
          + " JOIN note n ON n.id = r.note_id"
          + " WHERE r.received_at < :before"
          + " AND NOT EXISTS (SELECT 1 FROM note_like l WHERE l.note_id = r.note_id)"
          + " AND NOT EXISTS (SELECT 1 FROM note_repost p WHERE p.note_id = r.note_id)"
          + " AND NOT EXISTS (SELECT 1 FROM note_bookmark b WHERE b.note_id = r.note_id)"
          + " AND NOT EXISTS (SELECT 1 FROM note_poll_vote v WHERE v.note_id = r.note_id)"
          + " AND NOT EXISTS (SELECT 1 FROM note a"
          + " WHERE a.in_reply_to_id = r.note_id AND a.user_id IS NOT NULL)"
          + " AND NOT EXISTS (SELECT 1 FROM note q"
          + " WHERE q.quoted_note_id = r.note_id AND q.user_id IS NOT NULL)"
          + " AND NOT EXISTS (SELECT 1 FROM post_note_quote pq WHERE pq.note_id = r.note_id)"
          + " AND NOT EXISTS (SELECT 1 FROM federation_following f"
          + " WHERE f.remote_actor_id = n.remote_actor_id AND f.accepted_at IS NOT NULL)"
          + " ORDER BY r.received_at, r.note_id LIMIT :limit) expired)";

  private final EntityManager em;

  @Override
  public void mark(Long noteId, Long relayId, Instant receivedAt) {
    em.createNativeQuery(
            "INSERT INTO federation_relayed_note (note_id, relay_id, received_at)"
                + " VALUES (:note, :relay, :at)")
        .setParameter("note", noteId)
        .setParameter("relay", relayId)
        .setParameter("at", receivedAt)
        .executeUpdate();
  }

  @Override
  public long receivedSince(Instant since) {
    return ((Number)
            em.createNativeQuery(
                    "SELECT COUNT(*) FROM federation_relayed_note WHERE received_at >= :since")
                .setParameter("since", since)
                .getSingleResult())
        .longValue();
  }

  @Override
  @SuppressWarnings("unchecked")
  public Map<Long, Long> countByRelay() {
    List<Object[]> rows =
        em.createNativeQuery(
                "SELECT relay_id, COUNT(*) FROM federation_relayed_note GROUP BY relay_id")
            .getResultList();
    return rows.stream()
        .collect(
            Collectors.toUnmodifiableMap(
                row -> row[0] == null ? 0L : ((Number) row[0]).longValue(),
                row -> ((Number) row[1]).longValue()));
  }

  @Override
  @Transactional
  public int deleteExpired(Instant receivedBefore, int limit) {
    return em.createNativeQuery(DELETE_EXPIRED)
        .setParameter("before", receivedBefore)
        .setParameter("limit", limit)
        .executeUpdate();
  }
}
