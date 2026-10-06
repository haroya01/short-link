package com.example.short_link.note.infrastructure.persistence;

import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.NoteFeedRow;
import com.example.short_link.note.domain.repository.NoteRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class NoteRepositoryAdapter implements NoteRepository {

  private static final Duration TRENDING_WINDOW = Duration.ofDays(7);

  private final JpaNoteRepository jpa;

  @PersistenceContext private EntityManager em;

  @Override
  public NoteEntity save(NoteEntity note) {
    return jpa.save(note);
  }

  @Override
  public Optional<NoteEntity> findById(Long id) {
    return jpa.findById(id);
  }

  @Override
  public List<NoteEntity> findAllByIdIn(Collection<Long> ids) {
    return ids.isEmpty() ? List.of() : jpa.findAllByIdIn(ids);
  }

  @Override
  public void delete(NoteEntity note) {
    jpa.delete(note);
  }

  @Override
  public List<NoteEntity> topLevel(int offset, int limit) {
    return em.createQuery(
            "select n from NoteEntity n where n.inReplyToId is null order by n.id desc",
            NoteEntity.class)
        .setFirstResult(offset)
        .setMaxResults(limit)
        .getResultList();
  }

  @Override
  public List<NoteEntity> topLevelByAuthors(Collection<Long> authorIds, int offset, int limit) {
    if (authorIds.isEmpty()) {
      return List.of();
    }
    return em.createQuery(
            "select n from NoteEntity n where n.userId in :authors and n.inReplyToId is null"
                + " order by n.id desc",
            NoteEntity.class)
        .setParameter("authors", authorIds)
        .setFirstResult(offset)
        .setMaxResults(limit)
        .getResultList();
  }

  // Top-level notes written in the window, by reactions from people other than the author: likes,
  // reposts, replies and likes or boosts from other servers. Ties fall back to newest first.
  @Override
  @SuppressWarnings("unchecked")
  public List<NoteEntity> trending(int offset, int limit) {
    return em.createNativeQuery(
            "SELECT n.* FROM note n"
                + " WHERE n.in_reply_to_id IS NULL AND n.created_at >= :since"
                + " ORDER BY ("
                + "(SELECT COUNT(*) FROM note_like l WHERE l.note_id = n.id"
                + " AND l.user_id <> n.user_id)"
                + " + (SELECT COUNT(*) FROM note_repost r WHERE r.note_id = n.id"
                + " AND r.user_id <> n.user_id)"
                + " + (SELECT COUNT(*) FROM note c WHERE c.in_reply_to_id = n.id"
                + " AND c.user_id <> n.user_id)"
                + " + (SELECT COUNT(*) FROM note_remote_reaction x WHERE x.note_id = n.id)"
                + ") DESC, n.id DESC",
            NoteEntity.class)
        .setParameter("since", Instant.now().minus(TRENDING_WINDOW))
        .setFirstResult(offset)
        .setMaxResults(limit)
        .getResultList();
  }

  // Top-level notes by the authors and their reposts, one row per note at its newest activity; the
  // original wins a tie with a repost. A repost of someone the viewer blocked, or who blocked the
  // viewer, is left out.
  @Override
  public List<NoteFeedRow> following(
      Collection<Long> authorIds, Long viewerId, int offset, int limit) {
    if (authorIds.isEmpty()) {
      return List.of();
    }
    List<?> rows =
        em.createNativeQuery(
                "SELECT n.*, x.reposter_id FROM ("
                    + "SELECT y.note_id, y.at, y.reposter_id FROM ("
                    + "SELECT t.note_id, t.at, t.reposter_id, ROW_NUMBER() OVER ("
                    + "PARTITION BY t.note_id ORDER BY t.at DESC, t.reposter_id IS NULL DESC"
                    + ") AS position FROM ("
                    + "SELECT o.id AS note_id, o.created_at AS at, NULL AS reposter_id FROM note o"
                    + " WHERE o.user_id IN (:authors) AND o.in_reply_to_id IS NULL"
                    + " UNION ALL"
                    + " SELECT r.note_id, r.created_at, r.user_id FROM note_repost r"
                    + " JOIN note s ON s.id = r.note_id"
                    + " WHERE r.user_id IN (:authors) AND NOT EXISTS ("
                    + "SELECT 1 FROM user_block b"
                    + " WHERE (b.blocker_id = :viewer AND b.blocked_id = s.user_id)"
                    + " OR (b.blocker_id = s.user_id AND b.blocked_id = :viewer))"
                    + ") t) y WHERE y.position = 1"
                    + ") x JOIN note n ON n.id = x.note_id"
                    + " ORDER BY x.at DESC, x.note_id DESC LIMIT :limit OFFSET :offset",
                NoteEntity.FEED_MAPPING)
            .setParameter("authors", authorIds)
            .setParameter("viewer", viewerId)
            .setParameter("limit", limit)
            .setParameter("offset", offset)
            .getResultList();
    List<NoteFeedRow> feed = new ArrayList<>(rows.size());
    for (Object raw : rows) {
      Object[] cols = (Object[]) raw;
      feed.add(new NoteFeedRow((NoteEntity) cols[0], (Long) cols[1]));
    }
    return feed;
  }

  @Override
  public List<NoteEntity> replies(Long noteId, int limit) {
    return em.createQuery(
            "select n from NoteEntity n where n.inReplyToId = :noteId order by n.id asc",
            NoteEntity.class)
        .setParameter("noteId", noteId)
        .setMaxResults(limit)
        .getResultList();
  }

  @Override
  public Map<Long, Long> replyCounts(Collection<Long> noteIds) {
    Map<Long, Long> counts = new HashMap<>();
    if (noteIds.isEmpty()) {
      return counts;
    }
    for (Object[] row : jpa.replyCounts(noteIds)) {
      counts.put((Long) row[0], (Long) row[1]);
    }
    return counts;
  }

  @Override
  public long countByAuthor(Long userId) {
    return jpa.countByUserId(userId);
  }
}
