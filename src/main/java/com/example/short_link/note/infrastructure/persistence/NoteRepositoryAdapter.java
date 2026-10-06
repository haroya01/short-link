package com.example.short_link.note.infrastructure.persistence;

import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.NoteFeedRow;
import com.example.short_link.note.domain.NoteStats;
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

  // Replies, likes and reposts of a page in one statement; likes and boosts from other servers add
  // to likes and reposts.
  @Override
  public Map<Long, NoteStats> stats(Collection<Long> noteIds) {
    Map<Long, NoteStats> stats = new HashMap<>();
    if (noteIds.isEmpty()) {
      return stats;
    }
    List<?> rows =
        em.createNativeQuery(
                "SELECT s.note_id, s.kind, COUNT(*) FROM ("
                    + "SELECT in_reply_to_id AS note_id, 'REPLY' AS kind FROM note"
                    + " WHERE in_reply_to_id IN (:ids)"
                    + " UNION ALL SELECT note_id, 'LIKE' FROM note_like WHERE note_id IN (:ids)"
                    + " UNION ALL SELECT note_id, 'REPOST' FROM note_repost WHERE note_id IN (:ids)"
                    + " UNION ALL SELECT note_id, IF(kind = 'LIKE', 'LIKE', 'REPOST')"
                    + " FROM note_remote_reaction WHERE note_id IN (:ids)"
                    + ") s GROUP BY s.note_id, s.kind")
            .setParameter("ids", noteIds)
            .getResultList();
    for (Object raw : rows) {
      Object[] cols = (Object[]) raw;
      Long noteId = ((Number) cols[0]).longValue();
      long count = ((Number) cols[2]).longValue();
      NoteStats current = stats.getOrDefault(noteId, NoteStats.NONE);
      stats.put(
          noteId,
          switch (cols[1].toString()) {
            case "REPLY" -> new NoteStats(count, current.likes(), current.reposts());
            case "LIKE" -> new NoteStats(current.replies(), count, current.reposts());
            default -> new NoteStats(current.replies(), current.likes(), count);
          });
    }
    return stats;
  }

  @Override
  public long countByAuthor(Long userId) {
    return jpa.countByUserId(userId);
  }
}
