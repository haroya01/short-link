package com.example.short_link.federation.infrastructure.persistence;

import com.example.short_link.federation.domain.repository.FederationRelayedNoteRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.hibernate.query.NativeQuery;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
class FederationRelayedNoteRepositoryAdapter implements FederationRelayedNoteRepository {

  // A member's touch keeps the note: a like, repost, bookmark or poll vote, a reply or quote of
  // their own, a post quoting it, or a follow of its author.
  private static String unmarked(String note, String author) {
    return " AND NOT EXISTS (SELECT 1 FROM note_like l WHERE l.note_id = "
        + note
        + ") AND NOT EXISTS (SELECT 1 FROM note_repost p WHERE p.note_id = "
        + note
        + ") AND NOT EXISTS (SELECT 1 FROM note_bookmark b WHERE b.note_id = "
        + note
        + ") AND NOT EXISTS (SELECT 1 FROM note_poll_vote v WHERE v.note_id = "
        + note
        + ") AND NOT EXISTS (SELECT 1 FROM post_note_quote pq WHERE pq.note_id = "
        + note
        + ") AND NOT EXISTS (SELECT 1 FROM federation_following f"
        + " WHERE f.remote_actor_id = "
        + author
        + " AND f.accepted_at IS NOT NULL)";
  }

  // Found by a plain read, which locks nothing: a DELETE that searched itself would hold shared
  // next-key locks on every note it scanned. NO_SEMIJOIN keeps answers and quotes to one index
  // lookup per candidate instead of a scan of every note.
  private static final String EXPIRED =
      "SELECT r.note_id FROM federation_relayed_note r JOIN note n ON n.id = r.note_id"
          + " WHERE r.received_at < :before"
          + unmarked("r.note_id", "n.remote_actor_id")
          + " AND NOT EXISTS (SELECT /*+ NO_SEMIJOIN() */ 1 FROM note a"
          + " WHERE a.in_reply_to_id = r.note_id AND a.user_id IS NOT NULL)"
          + " AND NOT EXISTS (SELECT /*+ NO_SEMIJOIN() */ 1 FROM note q"
          + " WHERE q.quoted_note_id = r.note_id AND q.user_id IS NOT NULL)"
          + " ORDER BY r.received_at, r.note_id LIMIT :limit";

  // Checks the touches again for its own ids, so one that came after the read keeps its note. A
  // DELETE may read its own table only through a derived table; without the forced indexes MySQL
  // scans every note for it, since most rows have neither column set.
  private static final String DELETE_UNTOUCHED =
      "DELETE FROM note WHERE id IN (:ids)"
          + unmarked("note.id", "note.remote_actor_id")
          + " AND id NOT IN (SELECT kept.id FROM ("
          + "SELECT a.in_reply_to_id AS id FROM note a FORCE INDEX (idx_note_reply_created)"
          + " WHERE a.in_reply_to_id IN (:ids) AND a.user_id IS NOT NULL"
          + " UNION ALL SELECT q.quoted_note_id FROM note q FORCE INDEX (fk_note_quoted_note)"
          + " WHERE q.quoted_note_id IN (:ids) AND q.user_id IS NOT NULL) kept)";

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
  public void markPulled(Long noteId, String host, Instant receivedAt) {
    em.createNativeQuery(
            "INSERT INTO federation_relayed_note (note_id, pulled_from, received_at)"
                + " VALUES (:note, :host, :at)")
        .setParameter("note", noteId)
        .setParameter("host", host)
        .setParameter("at", receivedAt)
        .executeUpdate();
  }

  @Override
  public long relayedSince(Instant since) {
    return receivedSince(since, "IS NULL");
  }

  @Override
  public long pulledSince(Instant since) {
    return receivedSince(since, "IS NOT NULL");
  }

  private long receivedSince(Instant since, String pulled) {
    return ((Number)
            em.createNativeQuery(
                    "SELECT COUNT(*) FROM federation_relayed_note WHERE received_at >= :since"
                        + " AND pulled_from "
                        + pulled)
                .setParameter("since", since)
                .getSingleResult())
        .longValue();
  }

  @Override
  @SuppressWarnings("unchecked")
  public Optional<Instant> newestPulled(String host) {
    List<Instant> at =
        em.createNativeQuery(
                "SELECT n.created_at FROM note n WHERE n.id = (SELECT MAX(r.note_id)"
                    + " FROM federation_relayed_note r WHERE r.pulled_from = :host)")
            .setParameter("host", host)
            .unwrap(NativeQuery.class)
            .addScalar("created_at", Instant.class)
            .getResultList();
    return at.stream().findFirst();
  }

  @Override
  @SuppressWarnings("unchecked")
  public Map<Long, Long> countByRelay() {
    List<Object[]> rows =
        em.createNativeQuery(
                "SELECT relay_id, COUNT(*) FROM federation_relayed_note"
                    + " WHERE pulled_from IS NULL GROUP BY relay_id")
            .getResultList();
    return rows.stream()
        .collect(
            Collectors.toUnmodifiableMap(
                row -> row[0] == null ? 0L : ((Number) row[0]).longValue(),
                row -> ((Number) row[1]).longValue()));
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<Long> expired(Instant receivedBefore, int limit) {
    List<Number> ids =
        em.createNativeQuery(EXPIRED)
            .setParameter("before", receivedBefore)
            .setParameter("limit", limit)
            .getResultList();
    return ids.stream().map(Number::longValue).toList();
  }

  @Override
  @Transactional
  public int deleteUntouched(List<Long> noteIds) {
    if (noteIds.isEmpty()) {
      return 0;
    }
    return em.createNativeQuery(DELETE_UNTOUCHED).setParameter("ids", noteIds).executeUpdate();
  }
}
